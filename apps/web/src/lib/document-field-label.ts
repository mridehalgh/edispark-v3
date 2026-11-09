/** Business labels for validation locations; the exact JSON pointer remains available as evidence. */
export function documentFieldLabel(path?: string) {
  if (!path || path === "/") return "Document"
  return path
    .replace(
      /\/(OrderLine|InvoiceLine|DespatchLine|ReceiptLine|RemittanceAdviceLine)\/(\d+)/g,
      (_, _kind: string, index: string) => `/Line ${Number(index) + 1}`,
    )
    .split("/")
    .filter(
      (part) =>
        part && part !== "_" && !/^\d+$/.test(part) && part !== "LineItem",
    )
    .map((part) =>
      part
        .replace(/~1/g, "/")
        .replace(/~0/g, "~")
        .replace(/([a-z])([A-Z])/g, "$1 $2"),
    )
    .join(" › ")
}
