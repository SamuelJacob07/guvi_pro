# DevOps Pipeline Demo (classroom project)

A minimal, non-production, end-to-end CI/CD pipeline:

```
Code checkout → Maven build → Docker image → push to ECR → GitOps manifest update → ArgoCD sync → EKS
```

Stack: Java 17 + Spring Boot (app) · Jenkins (CI) · Amazon ECR (registry) · ArgoCD (CD, GitOps) · EKS (ap-south-1 / Mumbai)

## Repo layout

```
app/            Spring Boot app + Dockerfile
Jenkinsfile     CI pipeline (checkout, build, test, docker build, push, update manifest)
k8s/            Plain Kubernetes manifests ArgoCD watches and syncs
argocd/         ArgoCD Application definition (apply once, manually)
scripts/        Read-only helper scripts (AWS info gathering)
```

## How it fits together

1. You push code to your Git repo (the one already linked to ArgoCD).
2. Jenkins (on your client EC2) runs the `Jenkinsfile`: builds the jar, builds a Docker image, pushes it to ECR tagged with the Jenkins build number, then edits `k8s/deployment.yaml` to point at that new tag and pushes the commit back to the repo.
3. ArgoCD notices the Git change (it's already watching this repo) and syncs `k8s/` into the `devops-demo` namespace on your EKS cluster — that's the actual deploy.
4. You view the running app via the `LoadBalancer` Service's external DNS name.

Jenkins itself never touches the cluster — it only pushes an image and a Git commit. This is standard GitOps: Git is the source of truth, ArgoCD is the only thing with deploy access to the cluster.

## Step 0 — gather info from your AWS setup

Run this on your client EC2 (it's read-only, makes no changes):

```bash
./scripts/aws-eks-info.sh
```

**Already filled in for this environment:**
- AWS account: `208843796755`
- EKS cluster: `argocd` (region `ap-south-1`) — yes, the cluster is literally named `argocd`, same as the tool running on it. Don't confuse the two.
- Git repo: `https://github.com/SamuelJacob07/guvi_pro.git` (public)

Still needed: the **node group name**, to verify ECR pull permissions in Step 2 below.
```bash
aws eks list-nodegroups --cluster-name argocd --region ap-south-1
```

## Step 1 — create the ECR repository

```bash
aws ecr create-repository \
  --repository-name devops-demo-app \
  --region ap-south-1 \
  --image-scanning-configuration scanOnPush=true
```

## Step 2 — make sure EKS nodes can pull from ECR

Most EKS node IAM roles already have `AmazonEC2ContainerRegistryReadOnly` attached by default. Verify:

```bash
NODE_ROLE=$(aws eks describe-nodegroup --cluster-name argocd --nodegroup-name <NODEGROUP_NAME> --region ap-south-1 --query 'nodegroup.nodeRole' --output text)
aws iam list-attached-role-policies --role-name "$(basename "$NODE_ROLE")"
```
(fill in `<NODEGROUP_NAME>` from the `aws eks list-nodegroups` output above)

If `AmazonEC2ContainerRegistryReadOnly` isn't listed, attach it:

```bash
aws iam attach-role-policy --role-name "$(basename "$NODE_ROLE")" \
  --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly
```

## Step 3 — install Jenkins on the client EC2

Pick the block matching your EC2's OS.

**Amazon Linux 2023 / Amazon Linux 2** (this is what your client EC2 is running — `yum` works on both):
```bash
sudo yum install -y java-17-amazon-corretto maven git
sudo yum install -y docker
sudo systemctl enable --now docker
sudo usermod -aG docker $USER
sudo usermod -aG docker ec2-user

sudo wget -O /etc/yum.repos.d/jenkins.repo https://pkg.jenkins.io/redhat-stable/jenkins.repo
sudo rpm --import https://pkg.jenkins.io/redhat-stable/jenkins.io-2023.key
sudo yum install -y jenkins
sudo usermod -aG docker jenkins
sudo systemctl enable --now jenkins
```

**Ubuntu:**
```bash
sudo apt update
sudo apt install -y openjdk-17-jdk maven git docker.io
sudo usermod -aG docker $USER

curl -fsSL https://pkg.jenkins.io/debian-stable/jenkins.io-2023.key | sudo tee /usr/share/keyrings/jenkins-keyring.asc > /dev/null
echo "deb [signed-by=/usr/share/keyrings/jenkins-keyring.asc] https://pkg.jenkins.io/debian-stable binary/" | sudo tee /etc/apt/sources.list.d/jenkins.list > /dev/null
sudo apt update
sudo apt install -y jenkins
sudo usermod -aG docker jenkins
sudo systemctl enable --now jenkins
```

Then:
```bash
sudo systemctl restart jenkins   # picks up the docker group membership
sudo cat /var/lib/jenkins/secrets/initialAdminPassword   # initial unlock password
```

Open `http://<client-ec2-public-ip>:8080`, install suggested plugins, plus **Amazon ECR** and **Pipeline: AWS Steps** (or just rely on the AWS CLI already on the box, which is what the Jenkinsfile uses).

### Jenkins credentials to configure

- **AWS credentials**: give the EC2 instance an IAM role/instance profile with `ecr:GetAuthorizationToken`, `ecr:BatchCheckLayerAvailability`, `ecr:PutImage`, `ecr:InitiateLayerUpload`, `ecr:UploadLayerPart`, `ecr:CompleteLayerUpload` on the `devops-demo-app` repo (simplest for a classroom demo — no static keys to manage).
- **`git-push-creds`** (Jenkins → Manage Jenkins → Credentials → add "Username with password"): a Git personal access token with push rights to your repo. This is what the Jenkinsfile's "Update Manifest" stage uses to commit the new image tag back.

### Create the Jenkins pipeline job

1. New Item → Pipeline → name it `devops-demo-app`.
2. Pipeline → "Pipeline script from SCM" → Git → your repo URL → branch `main` → Script Path `Jenkinsfile`.
3. Save, then **Build Now**.

## Step 4 — register the app with ArgoCD

Push this whole project to your Git repo first (see below), fill in `argocd/application.yaml`'s `repoURL`, then:

```bash
kubectl apply -f argocd/application.yaml
argocd app sync devops-demo-app   # or just wait — automated sync is enabled
```

## Step 5 — see the output

```bash
kubectl get pods -n devops-demo
kubectl get svc -n devops-demo devops-demo-app
```

Wait for the Service's `EXTERNAL-IP` (a `*.elb.amazonaws.com` hostname) to appear, then:

```bash
curl http://<EXTERNAL-IP>/
# {"message":"Hello from the DevOps pipeline demo!","version":"<build-number>","podHostname":"devops-demo-app-..."}
```

Each time Jenkins runs, the `version` and `podHostname` values change — that's your visible proof the full pipeline (build → push → GitOps update → ArgoCD sync → new pods) actually ran.

## Pushing this project to your repo

```bash
cd /Users/samueljacob/Desktop/devops-project
git remote add origin <YOUR_GIT_REPO_URL>
git branch -M main
git push -u origin main
```

## Notes (why it's kept light)

- Single replica-light Deployment (2 pods), small resource requests, no HPA/Ingress/TLS — not meant for production.
- Uses a vanilla `LoadBalancer` Service (classic ELB) instead of an Ingress + ALB controller, to avoid installing extra cluster components.
- Jenkins runs natively on the existing client EC2 (no extra infra) — Docker builds happen on that box.
- No Helm — plain manifests are easier to read for a first GitOps demo.
