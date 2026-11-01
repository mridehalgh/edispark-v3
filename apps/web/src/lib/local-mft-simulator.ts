export type LocalSimulationDirection = "INBOUND" | "OUTBOUND"

export const LOCAL_MFT_SIMULATOR_EVENT = "edispark:mft-simulator-updated"

const SAMPLES = {
  INBOUND: {
    filename: "local-inbound-order.edi",
    contentType: "application/edifact",
    content: [
      "UNB+UNOC:3+[partner_identifier]+[tenant_identifier]+261101:0930+LOCALINBOUND1'",
      "UNH+1+ORDERS:D:96A:UN'", "BGM+220+LOCAL-INBOUND-001+9'", "DTM+137:20261101:102'",
      "DTM+2:20261105:102'", "RFF+CT:CONTRACT-42'", "RFF+PD:BUYER-REF-42'",
      "NAD+BY+[partner_identifier]::9++[name]+[address]+[address]++[address]+GB'", "CTA+PD+[name]'", "COM+[email]:EM'",
      "NAD+SU+[tenant_identifier]::9++[name]+[address]+[address]++[address]+GB'",
      "NAD+DP+STORE-042::92++[name]+[address]+[address]++[address]+GB'", "CUX+2:GBP:9'", "TOD+5++DAP'", "PAT+1++5:3:D:30'",
      "LIN+1++5010000000015:EN'", "PIA+1+BUY-WATER-750:BP+SPARK-WATER-750:SA'", "IMD+F++:::Spark Water 750'", "QTY+21:48:EA'", "PRI+AAA:2.25'", "MOA+203:108.00'",
      "LIN+2++5010000000022:EN'", "PIA+1+BUY-OAT-12:BP+OAT-BAR-12PK:SA'", "IMD+F++:::Oat Bar 12 Pack'", "QTY+21:24:EA'", "PRI+AAA:18.75'", "MOA+203:450.00'",
      "UNS+S'", "MOA+79:558.00'", "MOA+86:669.60'", "CNT+2:2'", "UNT+31+1'", "UNZ+1+LOCALINBOUND1'",
    ].join("\n"),
  },
  OUTBOUND: {
    filename: "local-outbound-invoice.edi",
    contentType: "application/edifact",
    content: [
      "UNB+UNOC:3+[tenant_identifier]+[partner_identifier]+261101:0930+LOCALOUTBOUND1'",
      "UNH+1+INVOIC:D:96A:UN'", "BGM+380+LOCAL-OUTBOUND-001+9'", "DTM+137:20261101:102'", "DTM+13:20261201:102'",
      "RFF+ON:PO-2026-0042'", "RFF+CT:CONTRACT-42'",
      "NAD+SU+[tenant_identifier]::9++[name]+[address]+[address]++[address]+GB'", "RFF+VA:[tax_identifier]'", "CTA+AR+[name]'", "COM+[email]:EM'",
      "NAD+BY+[partner_identifier]::9++[name]+[address]+[address]++[address]+GB'", "RFF+VA:[tax_identifier]'", "CUX+2:GBP:4'", "PAT+1++5:3:D:30'",
      "LIN+1++5010000000015:EN'", "PIA+1+BUY-WATER-750:BP+SPARK-WATER-750:SA'", "IMD+F++:::Spark Water 750'", "QTY+47:48:EA'", "MOA+203:108.00'", "PRI+AAA:2.25'", "TAX+7+VAT+++:::20+S'",
      "LIN+2++5010000000022:EN'", "PIA+1+BUY-OAT-12:BP+OAT-BAR-12PK:SA'", "IMD+F++:::Oat Bar 12 Pack'", "QTY+47:24:EA'", "MOA+203:450.00'", "PRI+AAA:18.75'", "TAX+7+VAT+++:::20+S'",
      "UNS+S'", "MOA+79:558.00'", "MOA+125:552.42'", "MOA+176:110.48'", "MOA+86:662.90'", "UNT+34+1'", "UNZ+1+LOCALOUTBOUND1'",
    ].join("\n"),
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
