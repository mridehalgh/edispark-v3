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
      "LIN+3++5010000000039:EN'", "PIA+1+BUY-COFFEE-1K:BP+ROAST-COFFEE-1K:SA'", "IMD+F++:::Roast Coffee 1 kg'", "QTY+21:36:EA'", "PRI+AAA:4.50'", "MOA+203:162.00'",
      "LIN+4++5010000000046:EN'", "PIA+1+BUY-DETERGENT-5L:BP+ECO-DETERGENT-5L:SA'", "IMD+F++:::Eco Detergent 5 L'", "QTY+21:12:EA'", "PRI+AAA:32.00'", "MOA+203:384.00'",
      "LIN+5++5010000000053:EN'", "PIA+1+BUY-PASSATA-6:BP+PASSATA-6PK:SA'", "IMD+F++:::Passata 6 Pack'", "QTY+21:60:EA'", "PRI+AAA:3.20'", "MOA+203:192.00'",
      "LIN+6++5010000000060:EN'", "PIA+1+BUY-TOWEL-24:BP+PAPER-TOWEL-24:SA'", "IMD+F++:::Paper Towel 24'", "QTY+21:20:EA'", "PRI+AAA:15.40'", "MOA+203:308.00'",
      "LIN+7++5010000000077:EN'", "PIA+1+BUY-OIL-6:BP+OLIVE-OIL-6PK:SA'", "IMD+F++:::Olive Oil 6 Pack'", "QTY+21:16:EA'", "PRI+AAA:22.50'", "MOA+203:360.00'",
      "LIN+8++5010000000084:EN'", "PIA+1+BUY-TEA-80:BP+HERBAL-TEA-80:SA'", "IMD+F++:::Herbal Tea 80'", "QTY+21:30:EA'", "PRI+AAA:6.80'", "MOA+203:204.00'",
      "UNS+S'", "MOA+79:2168.00'", "MOA+125:2146.32'", "MOA+176:429.26'", "MOA+86:2575.58'", "CNT+2:8'", "UNT+72+1'", "UNZ+1+LOCALINBOUND1'",
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
      "LIN+3++5010000000039:EN'", "PIA+1+BUY-COFFEE-1K:BP+ROAST-COFFEE-1K:SA'", "IMD+F++:::Roast Coffee 1 kg'", "QTY+47:36:EA'", "MOA+203:162.00'", "PRI+AAA:4.50'", "TAX+7+VAT+++:::20+S'",
      "LIN+4++5010000000046:EN'", "PIA+1+BUY-DETERGENT-5L:BP+ECO-DETERGENT-5L:SA'", "IMD+F++:::Eco Detergent 5 L'", "QTY+47:12:EA'", "MOA+203:384.00'", "PRI+AAA:32.00'", "TAX+7+VAT+++:::20+S'",
      "LIN+5++5010000000053:EN'", "PIA+1+BUY-PASSATA-6:BP+PASSATA-6PK:SA'", "IMD+F++:::Passata 6 Pack'", "QTY+47:60:EA'", "MOA+203:192.00'", "PRI+AAA:3.20'", "TAX+7+VAT+++:::20+S'",
      "LIN+6++5010000000060:EN'", "PIA+1+BUY-TOWEL-24:BP+PAPER-TOWEL-24:SA'", "IMD+F++:::Paper Towel 24'", "QTY+47:20:EA'", "MOA+203:308.00'", "PRI+AAA:15.40'", "TAX+7+VAT+++:::20+S'",
      "LIN+7++5010000000077:EN'", "PIA+1+BUY-OIL-6:BP+OLIVE-OIL-6PK:SA'", "IMD+F++:::Olive Oil 6 Pack'", "QTY+47:16:EA'", "MOA+203:360.00'", "PRI+AAA:22.50'", "TAX+7+VAT+++:::20+S'",
      "LIN+8++5010000000084:EN'", "PIA+1+BUY-TEA-80:BP+HERBAL-TEA-80:SA'", "IMD+F++:::Herbal Tea 80'", "QTY+47:30:EA'", "MOA+203:204.00'", "PRI+AAA:6.80'", "TAX+7+VAT+++:::20+S'",
      "UNS+S'", "MOA+79:2168.00'", "MOA+125:2146.32'", "MOA+176:429.26'", "MOA+86:2575.58'", "UNT+79+1'", "UNZ+1+LOCALOUTBOUND1'",
    ].join("\n"),
  },
} as const

function inboundBatch() {
  const source = SAMPLES.INBOUND.content
  const lines = source.split("\n")
  const start = lines.findIndex((line) => line.startsWith("UNH+"))
  const end = lines.findIndex((line) => line.startsWith("UNT+"))
  const secondMessage = lines.slice(start, end + 1).map((line) => line
    .replace("UNH+1+", "UNH+2+")
    .replace("BGM+220+LOCAL-INBOUND-001+", "BGM+220+LOCAL-INBOUND-002+")
    .replace("RFF+PD:BUYER-REF-42'", "RFF+PD:BUYER-REF-43'")
    .replace(/UNT\+(\d+)\+1'/, "UNT+$1+2'"))
  const unz = lines.findIndex((line) => line.startsWith("UNZ+"))
  lines.splice(unz, 0, ...secondMessage)
  lines[lines.length - 1] = lines[lines.length - 1].replace("UNZ+1+", "UNZ+2+")
  return { ...SAMPLES.INBOUND, filename: "local-inbound-orders-batch.edi", content: lines.join("\n") }
}

const INBOUND_BATCH = inboundBatch()

export function isLocalDevelopmentEnabled(nodeEnvironment: string | undefined) {
  return nodeEnvironment === "development"
}

export function localMftSample(direction: LocalSimulationDirection) {
  return direction === "INBOUND" ? INBOUND_BATCH : SAMPLES.OUTBOUND
}

export function isLocalSimulationDirection(value: unknown): value is LocalSimulationDirection {
  return value === "INBOUND" || value === "OUTBOUND"
}
