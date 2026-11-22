# EDIFACT standards module

This module owns syntax parsing/writing and qualified message schemas. The first qualified directory is D96A for ORDERS, INVOIC, and CONTRL. Partner-specific business mappings belong to the Transformations bounded context.

The bundled `edifact/d96a/orders.tsv` pins the [UNECE D96A ORDERS segment table](https://service.unece.org/trade/untdid/d96a/trmd/orders_s.htm) as position, message area, parent group path, tag, usage and maximum repeats. It was derived from the existing EdiSpark D96A `messages/orders.xml` mirror using depth-first order, ten position units per segment or group, and the published detail and summary boundaries. The TSV header records the source mirror's digest. A mandatory child of a conditional group is mandatory only when that group occurs.
