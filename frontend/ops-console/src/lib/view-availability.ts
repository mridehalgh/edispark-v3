import type { CapabilityKey, ConnectionState, EndpointCatalogue } from '@/lib/models'

export const capabilityAvailable = (
  catalogue: EndpointCatalogue | undefined,
  capabilityKey: CapabilityKey,
  connection: ConnectionState,
) => connection.status === 'healthy' && Boolean(catalogue?.capabilities[capabilityKey]?.available)

export const endpointFor = (catalogue: EndpointCatalogue | undefined, capabilityKey: CapabilityKey) =>
  catalogue?.capabilities[capabilityKey]
