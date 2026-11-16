resource "aws_iam_role_policy" "no_cloudformation_stacks" {
  for_each = var.role_bindings
  role     = aws_iam_role.ci[each.key].id
  policy = jsonencode({ Version = "2012-10-17", Statement = [{
    Effect = "Deny", Resource = "*", Action = [
      "cloudformation:CreateStack", "cloudformation:UpdateStack", "cloudformation:DeleteStack",
      "cloudformation:CreateChangeSet", "cloudformation:ExecuteChangeSet", "cloudformation:CreateStackSet",
      "cloudformation:UpdateStackSet", "cloudformation:DeleteStackSet", "cloudformation:CreateStackInstances",
      "cloudformation:UpdateStackInstances", "cloudformation:DeleteStackInstances"
    ]
    }, {
    Effect    = "Deny", Resource = "*",
    Action    = ["lambda:CreateWebFunction", "lambda:CreateWebFunctionEndpoint", "lambda:UpdateWebFunctionEndpoint"],
    Condition = { StringEquals = { "lambda:Request/WebFunctionAuthType" = "ApplicationManaged" } }
  }] })
}
