import type { CapabilityKey, ConnectionState, EndpointCatalogue } from '@/lib/models'

export type SurfaceDescriptor = {
  requiresBackend: boolean
  capabilityKey?: CapabilityKey
}

export const capabilityAvailable = (
  catalogue: EndpointCatalogue | undefined,
  capabilityKey: CapabilityKey,
  connection: ConnectionState,
) => connection.status === 'healthy' && Boolean(catalogue?.capabilities[capabilityKey]?.available)

export const endpointFor = (catalogue: EndpointCatalogue | undefined, capabilityKey: CapabilityKey) =>
  catalogue?.capabilities[capabilityKey]

export const surfaceAvailable = (
  surface: SurfaceDescriptor,
  catalogue: EndpointCatalogue | undefined,
  connection: ConnectionState,
) => {
  if (!surface.requiresBackend) {
    return true
  }

  if (!surface.capabilityKey) {
    return connection.status === 'healthy'
  }

  return capabilityAvailable(catalogue, surface.capabilityKey, connection)
}
