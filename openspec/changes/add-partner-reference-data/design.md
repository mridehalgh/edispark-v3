# Design

Publish immutable reference table revisions under tenant ownership. Use named text columns and a unique key column for the first slice. Preserve identifiers as entered, including leading zeroes. A route may pin a specific table revision. Publishing new data leaves existing pins intact.

The first consumer is Tesco inbound ORDERS. Preserve the retailer depot identifier and use the pinned table to add the delivery address and ERP site identifier. An unknown required key goes through the existing inbound review policy. A retained exchange can trace its table through its pinned agreement revision and capability.

Extend the existing managed setup later with route-specific business inputs, readiness results and test-to-live progress. Workspace legal and tax facts belong to the organisation. Retailer-specific account identifiers belong to the trading relationship. Connection and certificate settings remain owned by MFT.
