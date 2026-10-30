const initialJsonData = {
  workflowName: "EDI Workflow for Partner XYZ",
  workflowVersion: "1.0",
  workflowType: "outbound",
  steps: [
    {
      stepName: "Prepare EDI Transaction",
      stepType: "PREPARE",
      configuration: {
        version: "1.0",
        type: "X12"
      }
    },
    {
      stepName: "Batch Transactions",
      stepType: "BATCH",
      configuration: {
        version: "1.0",
        batchSize: 10,
        batchTimeout: 60000
      }
    },
    {
      stepName: "Send to Partner System A",
      stepType: "SEND",
      configuration: {
        version: "1.0",
        integrationType: "webhook",
        integrationConfig: {
          url: "https://example.com/webhook",
          method: "POST"
        }
      }
    },
    {
      stepName: "Send to Partner System B",
      stepType: "SEND",
      configuration: {
        version: "1.0",
        integrationType: "api",
        integrationConfig: {
          endpoint: "https://api.example.com/send",
          headers: {
            Authorization: "Bearer token"
          }
        }
      }
    }
  ]
};

const initialInboundJsonData = {
  workflowName: "Inbound EDI Workflow for Partner XYZ",
  workflowVersion: "1.0",
  workflowType: "inbound",
  steps: [
    {
      stepName: "Receive EDI Transaction",
      stepType: "RECEIVE",
      configuration: {
        version: "1.0",
        type: "AS2"
      }
    },
    {
      stepName: "Parse EDI Transaction",
      stepType: "PARSE",
      configuration: {
        version: "1.0",
        type: "X12"
      }
    },
    {
      stepName: "Map to Internal Format",
      stepType: "MAP",
      configuration: {
        version: "1.0",
        targetFormat: "InternalFormat"
      }
    },
    {
      stepName: "Integrate with Internal System",
      stepType: "INTEGRATE",
      configuration: {
        version: "1.0",
        integrationType: "api",
        integrationConfig: {
          endpoint: "https://internal.example.com/integrate",
          headers: {
            Authorization: "Bearer token"
          }
        }
      }
    }
  ]
};

export { initialJsonData, initialInboundJsonData };
