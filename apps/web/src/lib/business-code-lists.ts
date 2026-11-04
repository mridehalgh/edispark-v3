export type BusinessCodeOption = Readonly<{
  value: string
  label: string
}>

// UBL 2.1 qualifies quantity units against UN/ECE Recommendation 20.
// Keep this focused on units used in retail documents; partner profiles may narrow the list further.
export const QUANTITY_UNITS = [
  { value: "EA", label: "Each" },
  { value: "C62", label: "Unit" },
  { value: "H87", label: "Piece" },
  { value: "DZN", label: "Dozen" },
  { value: "DPC", label: "Dozen pieces" },
  { value: "PR", label: "Pair" },
  { value: "SET", label: "Set" },
  { value: "KGM", label: "Kilogram" },
  { value: "GRM", label: "Gram" },
  { value: "TNE", label: "Tonne" },
  { value: "LBR", label: "Pound" },
  { value: "ONZ", label: "Ounce" },
  { value: "LTR", label: "Litre" },
  { value: "MLT", label: "Millilitre" },
  { value: "MTR", label: "Metre" },
  { value: "CMT", label: "Centimetre" },
  { value: "MMT", label: "Millimetre" },
  { value: "MTK", label: "Square metre" },
  { value: "MTQ", label: "Cubic metre" },
] as const satisfies readonly BusinessCodeOption[]

export const WEIGHT_UNITS = [
  { value: "KGM", label: "Kilograms" },
  { value: "GRM", label: "Grams" },
  { value: "TNE", label: "Tonnes" },
  { value: "LBR", label: "Pounds" },
  { value: "ONZ", label: "Ounces" },
] as const satisfies readonly BusinessCodeOption[]

// ISO 4217 currencies commonly exchanged by UK and international retail partners.
export const CURRENCIES = [
  { value: "GBP", label: "Pound sterling" },
  { value: "EUR", label: "Euro" },
  { value: "USD", label: "US dollar" },
  { value: "CAD", label: "Canadian dollar" },
  { value: "AUD", label: "Australian dollar" },
  { value: "NZD", label: "New Zealand dollar" },
  { value: "CHF", label: "Swiss franc" },
  { value: "DKK", label: "Danish krone" },
  { value: "NOK", label: "Norwegian krone" },
  { value: "SEK", label: "Swedish krona" },
  { value: "PLN", label: "Polish zloty" },
  { value: "CZK", label: "Czech koruna" },
  { value: "JPY", label: "Japanese yen" },
  { value: "CNY", label: "Chinese yuan" },
  { value: "HKD", label: "Hong Kong dollar" },
  { value: "SGD", label: "Singapore dollar" },
] as const satisfies readonly BusinessCodeOption[]

// ISO 3166-1 alpha-2 countries most commonly used by the current retail workflows.
export const COUNTRIES = [
  { value: "GB", label: "United Kingdom" },
  { value: "IE", label: "Ireland" },
  { value: "FR", label: "France" },
  { value: "DE", label: "Germany" },
  { value: "NL", label: "Netherlands" },
  { value: "BE", label: "Belgium" },
  { value: "ES", label: "Spain" },
  { value: "IT", label: "Italy" },
  { value: "PL", label: "Poland" },
  { value: "DK", label: "Denmark" },
  { value: "NO", label: "Norway" },
  { value: "SE", label: "Sweden" },
  { value: "CH", label: "Switzerland" },
  { value: "US", label: "United States" },
  { value: "CA", label: "Canada" },
  { value: "AU", label: "Australia" },
  { value: "NZ", label: "New Zealand" },
  { value: "CN", label: "China" },
  { value: "IN", label: "India" },
] as const satisfies readonly BusinessCodeOption[]

// UN/EDIFACT data element 4461, also used by the UBL 2.1 PaymentMeansCode qualification.
export const PAYMENT_MEANS = [
  { value: "10", label: "Cash" },
  { value: "20", label: "Cheque" },
  { value: "30", label: "Credit transfer" },
  { value: "31", label: "Debit transfer" },
  { value: "42", label: "Payment to bank account" },
  { value: "48", label: "Bank card" },
  { value: "49", label: "Direct debit" },
  { value: "57", label: "Standing agreement" },
  { value: "58", label: "SEPA credit transfer" },
  { value: "59", label: "SEPA direct debit" },
] as const satisfies readonly BusinessCodeOption[]

// UN/EDIFACT data element 5305 tax category codes used by invoice profiles.
export const TAX_CATEGORIES = [
  { value: "S", label: "Standard rate" },
  { value: "Z", label: "Zero rated" },
  { value: "E", label: "Exempt" },
  { value: "AE", label: "Reverse charge" },
  { value: "G", label: "Export, tax not charged" },
  { value: "O", label: "Outside tax scope" },
  { value: "K", label: "Intra-community supply" },
] as const satisfies readonly BusinessCodeOption[]

export const HANDLING_UNIT_TYPES = [
  { value: "PALLET", label: "Pallet" },
  { value: "CARTON", label: "Carton" },
  { value: "ROLL_CAGE", label: "Roll cage" },
  { value: "CONTAINER", label: "Container" },
  { value: "PARCEL", label: "Parcel" },
  { value: "CRATE", label: "Crate" },
] as const satisfies readonly BusinessCodeOption[]

export const ORDER_RESPONSE_CODES = [
  { value: "AB", label: "Accepted" },
  { value: "AP", label: "Accepted with changes" },
  { value: "RE", label: "Rejected" },
] as const satisfies readonly BusinessCodeOption[]

