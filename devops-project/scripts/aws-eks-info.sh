#!/usr/bin/env bash
# Read-only. Run this on the client EC2 (or anywhere with AWS CLI configured)
# and paste the output back so the pipeline files can be filled in with real values.
set -euo pipefail

REGION="ap-south-1"

echo "== AWS account =="
aws sts get-caller-identity

echo
echo "== EKS clusters in ${REGION} =="
aws eks list-clusters --region "${REGION}"

read -rp "Enter your EKS cluster name from the list above: " CLUSTER_NAME

echo
echo "== Cluster details =="
aws eks describe-cluster --name "${CLUSTER_NAME}" --region "${REGION}" \
  --query 'cluster.{Status:status,Endpoint:endpoint,Version:version,VpcId:resourcesVpcConfig.vpcId,SubnetIds:resourcesVpcConfig.subnetIds}'

echo
echo "== Node groups =="
aws eks list-nodegroups --cluster-name "${CLUSTER_NAME}" --region "${REGION}"

echo
echo "== Existing ECR repositories =="
aws ecr describe-repositories --region "${REGION}" --query 'repositories[].repositoryName' || true

echo
echo "== Current kubectl context (run manually on the client EC2) =="
echo "kubectl config current-context"
echo "kubectl get nodes"
echo "kubectl get pods -n argocd"
