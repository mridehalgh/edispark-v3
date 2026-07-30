const workflowSchema = {
  $schema: "http://json-schema.org/draft-07/schema#",
  title: "EDI Workflow Configuration",
  type: "object",
  properties: {
    workflowName: {
      type: "string",
      description: "The name of the EDI workflow."
    },
    workflowVersion: {
      type: "string",
      description: "The version of the EDI workflow."
    },
    workflowType: {
      type: "string",
      enum: ["inbound", "outbound"],
      description: "The type of the workflow (inbound or outbound)."
    },
    steps: {
      type: "array",
      items: {
        type: "object",
        properties: {
          stepName: {
            type: "string",
            description: "The name of the workflow step."
          },
          stepType: {
            type: "string",
            enum: ["RECEIVE", "PARSE", "MAP", "INTEGRATE", "PREPARE", "BATCH", "SEND"],
            description: "The type of the workflow step."
          },
          configuration: {
            type: "object",
            properties: {
              version: {
                type: "string",
                description: "The version of the step configuration."
              },
              type: {
                type: "string",
                description: "The type of EDI preparation or parsing to use.",
                enum: ["X12", "EDIFACT"]
              },
              batchSize: {
                type: "integer",
                description: "The maximum number of transactions per batch."
              },
              batchTimeout: {
                type: "integer",
                description: "The maximum time to wait before sending a batch (in milliseconds)."
              },
              integrationType: {
                type: "string",
                description: "The type of integration to perform.",
                enum: ["webhook", "api"]
              },
              integrationConfig: {
                type: "object",
                description: "Configuration for the integration step.",
                properties: {
                  url: {
                    type: "string",
                    description: "The URL for webhook integration."
                  },
                  method: {
                    type: "string",
                    description: "The HTTP method for webhook integration.",
                    enum: ["GET", "POST", "PUT", "DELETE"]
                  },
                  endpoint: {
                    type: "string",
                    description: "The API endpoint for integration."
                  },
                  headers: {
                    type: "object",
                    description: "Headers for the API integration.",
                    additionalProperties: {
                      type: "string"
                    }
                  }
                }
              }
            }
          }
        },
        required: ["stepName", "stepType", "configuration"]
      }
    }
  },
  required: ["workflowName", "workflowVersion", "workflowType", "steps"]
};

export default workflowSchema;
