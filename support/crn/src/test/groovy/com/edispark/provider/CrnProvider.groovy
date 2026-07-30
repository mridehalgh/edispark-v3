package com.edispark.provider

import com.edispark.identifiers.region.Region
import com.edispark.identifiers.resource.Resource
import com.edispark.identifiers.resource.ResourceId
import com.edispark.identifiers.resource.ResourceType
import com.edispark.identifiers.service.Service
import com.edispark.identifiers.tenant.TenantId

class CrnProvider {

    public static final PARTITION_STRING = "edispark"
    public static final PARTITION = "edispark"
    public static final SERVICE_STRING = "documents"
    public static final SERVICE = Service.fromString(SERVICE_STRING)
    public static final REGION_STRING = "eu"
    public static final REGION = Region.fromString(REGION_STRING)
    public static final TENANT_ID_STRING = "2OnHVzeAJKwm0ThFLcy1HfLHGPm"
    public static final TENANT_ID = TenantId.fromString(TENANT_ID_STRING)
    public static final RESOURCE_TYPE_STRING = "order"
    public static final RESOURCE_TYPE = ResourceType.fromString(RESOURCE_TYPE_STRING)
    public static final RESOURCE_ID_STRING = "2OnHmVjaQa5eGWfT0HROcJkJmQ2"
    public static final RESOURCE_ID = ResourceId.fromString(RESOURCE_ID_STRING)
    public static final RESOURCE = new Resource(RESOURCE_TYPE, RESOURCE_ID)
    public static final SEP = ":"
    public static final STRING_CRN = "crn:edispark:documents:eu:2OnHVzeAJKwm0ThFLcy1HfLHGPm:order/2OnHmVjaQa5eGWfT0HROcJkJmQ2"
    public static final BASIC_STRING_CRN = "crn:edispark:documents:eu:2OnHVzeAJKwm0ThFLcy1HfLHGPm:2OnHmVjaQa5eGWfT0HROcJkJmQ2"
    public static final COMPLICATED_CRN = "crn:edispark:documents:eu:2OnHVzeAJKwm0ThFLcy1HfLHGPm:order/file/2OnHmVjaQa5eGWfT0HROcJkJmQ2"
    public static final COMPLICATED_CRN_WITH_EXTENSION = "crn:edispark:documents:eu:2OnHVzeAJKwm0ThFLcy1HfLHGPm:order/file/2OnHmVjaQa5eGWfT0HROcJkJmQ2.txt"

    public static final BAD_BASIC_CRN = "brn:edispark:documents:eu:2OnHVzeAJKwm0ThFLcy1HfLHGPm:2OnHmVjaQa5eGWfT0HROcJkJmQ2"
    public static final BAD_EXTENDED_CRN = "crn:edispark:bobsteam:documents:eu:2OnHVzeAJKwm0ThFLcy1HfLHGPm:2OnHmVjaQa5eGWfT0HROcJkJmQ2"

    public static final GLOBAL_CRN = "crn:edispark:config::2OnHVzeAJKwm0ThFLcy1HfLHGPm:2OnHmVjaQa5eGWfT0HROcJkJmQ2"

}
