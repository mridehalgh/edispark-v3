package com.edispark.rules

import spock.lang.Specification

class RestrictedCharactersTest extends Specification {

    def "restricts characters"() {
        when: "a #result character is provided then"
        def restrictedCharacters = new RestrictedCharacters(input)

        then:
        restrictedCharacters.isValid() == valid

        where:
        result    | input                           || valid
        "valid"   | ""                              || true
        "valid"   | "acbdefg"                       || true
        "valid"   | "abcde."                        || true
        "valid"   | "abcde/eff"                     || true
        "valid"   | "abcde.txt"                     || true
        "valid"   | "ab/cde.txt"                    || true
        "valid"   | "a_b/cd-e.txt"                  || true
        "invalid" | "a_b/cd-*e.txt"                 || false
        "invalid" | "a_b/cd-e:.txt"                 || false
        "invalid" | "a_b/cd-e:.txt"                 || false
        "invalid" | "a_b/cd-e:.txt"                 || false
        "invalid" | "a_b/cd-e:.txt"                 || false
        "invalid" | "a£_b/cd-e:.txt"                || false
        "invalid" | "a_^b/cd-e:.txt"                || false
        "invalid" | "a_b@/cd-e:.txt"                || false
        "invalid" | "a_b/&cd-e:.txt"                || false
        "invalid" | "a_b()423784*3427%13/cd-e:.txt" || false
    }
}
