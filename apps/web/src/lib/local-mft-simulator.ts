export type LocalSimulationDirection = "INBOUND" | "OUTBOUND"

export const LOCAL_MFT_SIMULATOR_EVENT = "edispark:mft-simulator-updated"

const SAMPLES = {
  INBOUND: {
    filename: "local-inbound-order.edi",
    contentType: "application/edifact",
    content: "UNH+1+ORDERS:D:96A:UN'BGM+220+LOCAL-INBOUND-001+9'NAD+BY+[buyer_id]++[name]+[address]'UNT+4+1'",
  },
  OUTBOUND: {
    filename: "local-outbound-invoice.edi",
    contentType: "application/edifact",
    content: "UNH+1+INVOIC:D:96A:UN'BGM+380+LOCAL-OUTBOUND-001+9'NAD+BY+[buyer_id]++[name]+[address]'UNT+4+1'",
  },
} as const

export function isLocalDevelopmentEnabled(nodeEnvironment: string | undefined) {
  return nodeEnvironment === "development"
}

export function localMftSample(direction: LocalSimulationDirection) {
  return SAMPLES[direction]
}

export function isLocalSimulationDirection(value: unknown): value is LocalSimulationDirection {
  return value === "INBOUND" || value === "OUTBOUND"
}
