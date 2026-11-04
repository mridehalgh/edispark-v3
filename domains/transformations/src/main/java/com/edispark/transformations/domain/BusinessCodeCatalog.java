package com.edispark.transformations.domain;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class BusinessCodeCatalog {
    private final Map<BusinessCodeList, BusinessCodeListDefinition> definitions;

    public BusinessCodeCatalog() {
        this(defaultDefinitions());
    }

    public BusinessCodeCatalog(List<BusinessCodeListDefinition> suppliedDefinitions) {
        definitions = new EnumMap<>(BusinessCodeList.class);
        suppliedDefinitions.forEach(definition -> {
            validateRepresentations(definition);
            if (definitions.put(definition.id(), definition) != null) {
                throw new IllegalArgumentException("Business code list ids must be unique");
            }
        });
    }

    private static List<BusinessCodeListDefinition> defaultDefinitions() {
        Map<BusinessCodeList, BusinessCodeListDefinition> defaults = new EnumMap<>(BusinessCodeList.class);
        register(defaults, BusinessCodeList.UNIT_OF_MEASURE, "UN/ECE Recommendation 20", units());
        register(defaults, BusinessCodeList.WEIGHT_UNIT, "UN/ECE Recommendation 20", weights());
        register(defaults, BusinessCodeList.CURRENCY, "ISO 4217", currencies());
        register(defaults, BusinessCodeList.COUNTRY, "ISO 3166-1 alpha-2", countries());
        register(defaults, BusinessCodeList.PAYMENT_MEANS, "UN/EDIFACT data element 4461", paymentMeans());
        register(defaults, BusinessCodeList.TAX_CATEGORY, "UNCL 5305 / EN 16931", taxCategories());
        register(defaults, BusinessCodeList.ORDER_RESPONSE, "UN/EDIFACT D.96A data element 4343", orderResponses());
        register(defaults, BusinessCodeList.HANDLING_UNIT_TYPE, "OASIS UBL / UN/ECE Recommendation 21",
                handlingUnits());
        return List.copyOf(defaults.values());
    }

    private static void register(Map<BusinessCodeList, BusinessCodeListDefinition> target,
            BusinessCodeList id, String authority, List<BusinessCode> values) {
        target.put(id, new BusinessCodeListDefinition(id, authority, "UBL 2.1 / EDIFACT D.96A / TRADACOMS 9",
                true, values));
    }

    private static void validateRepresentations(BusinessCodeListDefinition definition) {
        for (BusinessCodeSyntax syntax : BusinessCodeSyntax.values()) {
            List<String> representations = definition.values().stream()
                    .map(BusinessCode::representations)
                    .map(values -> values.get(syntax))
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (representations.stream().distinct().count() != representations.size()) {
                throw new IllegalArgumentException(syntax + " representations must be unique within "
                        + definition.id());
            }
        }
    }

    public Map<String, String> translationMap(BusinessCodeList id, BusinessCodeSyntax source,
            BusinessCodeSyntax target) {
        return get(id).values().stream()
                .filter(value -> value.representations().containsKey(source)
                        && value.representations().containsKey(target))
                .collect(Collectors.toUnmodifiableMap(
                        value -> value.representations().get(source),
                        value -> value.representations().get(target)));
    }

    @Deprecated(forRemoval = false)
    public Map<String, String> identityMap(BusinessCodeList id) {
        return translationMap(id, BusinessCodeSyntax.UBL_2_1, BusinessCodeSyntax.UBL_2_1);
    }

    public List<BusinessCodeListDefinition> list() {
        return List.copyOf(definitions.values());
    }

    public BusinessCodeListDefinition get(BusinessCodeList id) {
        BusinessCodeListDefinition definition = definitions.get(id);
        if (definition == null) throw new IllegalArgumentException("Unknown business code list: " + id);
        return definition;
    }

    public Map<String, String> reverse(Map<String, String> mappings) {
        if (mappings.values().stream().distinct().count() != mappings.size()) {
            throw new IllegalArgumentException("Code mapping cannot be reversed because target codes are not unique");
        }
        return mappings.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getValue, Map.Entry::getKey));
    }

    private static BusinessCode code(String code, String label) {
        return represented(code, label, code, code);
    }

    private static BusinessCode withoutEdifact(String code, String label) {
        return new BusinessCode(code, label, Map.of(
                BusinessCodeSyntax.UBL_2_1, code,
                BusinessCodeSyntax.TRADACOMS_9, code));
    }

    private static BusinessCode represented(String code, String label, String edifact, String tradacoms) {
        return new BusinessCode(code, label, Map.of(
                BusinessCodeSyntax.UBL_2_1, code,
                BusinessCodeSyntax.EDIFACT_D96A, edifact,
                BusinessCodeSyntax.TRADACOMS_9, tradacoms));
    }

    private static List<BusinessCode> units() {
        return List.of(code("EA", "Each"), code("C62", "Unit"), code("H87", "Piece"),
                code("DZN", "Dozen"), code("DPC", "Dozen pieces"), code("PR", "Pair"), code("SET", "Set"),
                code("KGM", "Kilogram"), code("GRM", "Gram"), code("TNE", "Metric tonne"),
                code("LBR", "Pound"), code("ONZ", "Ounce"), code("LTR", "Litre"),
                code("MLT", "Millilitre"), code("MTR", "Metre"), code("CMT", "Centimetre"),
                code("MMT", "Millimetre"), code("MTK", "Square metre"), code("MTQ", "Cubic metre"));
    }

    private static List<BusinessCode> weights() {
        return List.of(code("KGM", "Kilogram"), code("GRM", "Gram"), code("TNE", "Metric tonne"),
                code("LBR", "Pound"), code("ONZ", "Ounce"));
    }

    private static List<BusinessCode> currencies() {
        return codes("GBP:British pound", "EUR:Euro", "USD:US dollar", "CAD:Canadian dollar",
                "AUD:Australian dollar", "NZD:New Zealand dollar", "CHF:Swiss franc", "DKK:Danish krone",
                "NOK:Norwegian krone", "SEK:Swedish krona", "PLN:Polish zloty", "CZK:Czech koruna",
                "JPY:Japanese yen", "CNY:Chinese yuan", "HKD:Hong Kong dollar", "SGD:Singapore dollar");
    }

    private static List<BusinessCode> countries() {
        return codes("GB:United Kingdom", "IE:Ireland", "FR:France", "DE:Germany", "NL:Netherlands",
                "BE:Belgium", "ES:Spain", "IT:Italy", "PL:Poland", "DK:Denmark", "NO:Norway", "SE:Sweden",
                "CH:Switzerland", "US:United States", "CA:Canada", "AU:Australia", "NZ:New Zealand",
                "CN:China", "IN:India");
    }

    private static List<BusinessCode> paymentMeans() {
        return List.of(code("10", "Cash"), code("20", "Cheque"), code("30", "Credit transfer"),
                code("31", "Debit transfer"), code("42", "Payment to bank account"), code("48", "Bank card"),
                code("49", "Direct debit"), withoutEdifact("57", "Standing agreement"),
                withoutEdifact("58", "SEPA credit transfer"), withoutEdifact("59", "SEPA direct debit"));
    }

    private static List<BusinessCode> taxCategories() {
        return List.of(code("S", "Standard rate"), code("Z", "Zero rated"), code("E", "Exempt"),
                withoutEdifact("AE", "Reverse charge"), code("G", "Free export item"),
                code("O", "Outside scope"), withoutEdifact("K", "Intra-community supply"));
    }

    private static List<BusinessCode> orderResponses() {
        return codes("AB:Acknowledged", "AP:Accepted", "RE:Rejected");
    }

    private static List<BusinessCode> handlingUnits() {
        return List.of(represented("PALLET", "Pallet", "PX", "PALLET"),
                represented("CARTON", "Carton", "CT", "CARTON"),
                represented("ROLL_CAGE", "Roll cage", "CW", "ROLL_CAGE"),
                represented("CONTAINER", "Container", "CN", "CONTAINER"),
                represented("PARCEL", "Parcel", "PC", "PARCEL"),
                represented("CRATE", "Crate", "CR", "CRATE"));
    }

    private static List<BusinessCode> codes(String... values) {
        return Arrays.stream(values).map(value -> value.split(":", 2))
                .map(parts -> code(parts[0], parts[1])).toList();
    }
}
