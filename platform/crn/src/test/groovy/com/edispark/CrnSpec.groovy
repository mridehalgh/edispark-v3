package com.edispark

import com.edispark.identifiers.resource.Resource
import spock.lang.Specification

import static com.edispark.provider.CrnProvider.*

class CrnSpec extends Specification {

    def "produces an crn"() {
        when: "a valid crn is provided"
        def crn = new Crn(SERVICE, REGION, TENANT_ID, RESOURCE)

        then: "the correct string is provided"
        def resourceString = RESOURCE_TYPE_STRING + "/" + RESOURCE_ID_STRING
        def expectedCrn = ['crn', PARTITION_STRING, SERVICE_STRING, REGION_STRING, TENANT_ID_STRING, resourceString]
        def expectedCrnString = expectedCrn.join(SEP)

        crn.asString() == expectedCrnString
    }

    def "can parse string crn"() {
        when: "a crn in string form parsed the correct"
        def crnFromString = Crn.fromString(STRING_CRN)

        then: "the correct crn is returned as an object"
        def expectedCrn = new Crn(SERVICE, REGION, TENANT_ID, RESOURCE)

        crnFromString == expectedCrn
        crnFromString.asString() == STRING_CRN
    }

    def "can parse basic crn"() {
        when: "a crn in string form parsed the correct"
        def crnFromString = Crn.fromString(BASIC_STRING_CRN)

        then: "the correct crn is returned as an object"
        def expectedCrn = new Crn(SERVICE, REGION, TENANT_ID, Resource.fromString("2OnHmVjaQa5eGWfT0HROcJkJmQ2"))

        crnFromString == expectedCrn
        crnFromString.asString() == BASIC_STRING_CRN
    }

    def "can parse a complicated crn"() {
        when: "a crn in string form parsed the correct"
        def crnFromString = Crn.fromString(COMPLICATED_CRN)

        then: "the correct crn is returned as an object"
        def expectedCrn = new Crn(SERVICE, REGION, TENANT_ID, Resource.fromString("order/file/2OnHmVjaQa5eGWfT0HROcJkJmQ2"))

        crnFromString == expectedCrn
        crnFromString.asString() == COMPLICATED_CRN
    }

    def "can parse a complicated crn with extension"() {
        when: "a crn in string form parsed the correct"
        def crnFromString = Crn.fromString(COMPLICATED_CRN_WITH_EXTENSION)

        then: "the correct crn is returned as an object"
        def expectedCrn = new Crn(SERVICE, REGION, TENANT_ID, Resource.fromString("order/file/2OnHmVjaQa5eGWfT0HROcJkJmQ2.txt"))

        crnFromString == expectedCrn
        crnFromString.asString() == COMPLICATED_CRN_WITH_EXTENSION
    }

    def "throws an error when an incorrect crn is provided"() {
        when: "a incorrect CRN is parsed"
        Crn.fromString(crn)

        then: "an error is thrown"
        thrown(MalformedCrnException)

        where:
        crn              || excpetionType
        BAD_BASIC_CRN    || MalformedCrnException
        BAD_EXTENDED_CRN || MalformedCrnException
        " "              || MalformedCrnException
        null             || MalformedCrnException
    }

    def "can support global crn"() {
        when: "a global crn is provided"
        def crn = Crn.fromString(GLOBAL_CRN)

        then: "the parser handles it correctly"
        crn.asString() == GLOBAL_CRN

    }
}
