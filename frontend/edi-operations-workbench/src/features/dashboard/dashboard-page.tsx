import { Link } from 'react-router-dom'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { EmptyState, ErrorState, LoadingState } from '@/components/workbench-states'
import { useDocumentSetCatalogue } from '@/features/document-sets/use-document-set-catalogue'
import { projectRetailJourneys } from '@/features/journeys/retail-journeys'
import { useIntegration } from '@/integration/integration-provider'

export function DashboardPage() {
  const { contractState, refreshContract } = useIntegration()
  const { refresh, requestState } = useDocumentSetCatalogue()

  if (contractState.kind === 'loading-contract') {
    return <LoadingState title="Connecting to the local backend" description="Loading the live OpenAPI contract before dashboard metrics render." />
  }

  if (contractState.kind === 'contract-failed') {
    return (
      <ErrorState
        title="The dashboard could not load"
        description="The workbench cannot fetch its initial application state until the local backend contract is reachable."
        details={contractState.reason}
        onRetry={() => void refreshContract()}
        retryLabel="Retry connection"
      />
    )
  }

  if (requestState.status === 'pending' || requestState.status === 'idle') {
    return <LoadingState title="Loading retail evidence" description="Fetching document-set summaries for the dashboard overview." />
  }

  if (requestState.status === 'failed') {
    return (
      <ErrorState
        title="Retail dashboard data is unavailable"
        description="The document-set catalogue could not be loaded from the live backend response."
        details={requestState.reason}
        onRetry={() => void refresh()}
      />
    )
  }

  const documentSetPage = requestState.data
  const journeys = projectRetailJourneys(documentSetPage.items)
  const totalDocuments = documentSetPage.items.reduce((count, documentSet) => count + documentSet.documents.length, 0)

  return (
    <div className="space-y-6">
      <div className="grid gap-4 md:grid-cols-3">
        <MetricCard title="Document sets" value={`${documentSetPage.items.length}`} description="Live records available to power current journey views." />
        <MetricCard title="Documents" value={`${totalDocuments}`} description="Document summaries linked across all returned document sets." />
        <MetricCard title="Retail journeys" value={`${journeys.length}`} description="Curated UK retail flows mounted in the current shell." />
      </div>

      {documentSetPage.items.length === 0 ? (
        <EmptyState
          title="No live retail records yet"
          description="Retail dashboards populate from document sets returned by the Documents API. Add live document sets to start surfacing ORDERS, DESADV, RECADV, and INVOIC evidence."
          action={
            <Button asChild variant="outline">
              <Link to="/retail-journeys">Review journey expectations</Link>
            </Button>
          }
        />
      ) : null}

      <div className="grid gap-4 xl:grid-cols-2">
        {journeys.map((journey) => (
          <Card key={journey.key} className="border-border/70 bg-card/95 shadow-sm">
            <CardHeader className="space-y-4">
              <div className="flex items-start justify-between gap-4">
                <div className="space-y-2">
                  <Badge className="w-fit">{journey.title}</Badge>
                  <div>
                    <CardTitle>{journey.headline}</CardTitle>
                    <CardDescription>{journey.summary}</CardDescription>
                  </div>
                </div>
                <Button asChild variant="outline">
                  <Link to={`/retail-journeys/${journey.key}`}>Open journey</Link>
                </Button>
              </div>
            </CardHeader>
            <CardContent className="space-y-4 text-sm text-muted-foreground">
              <div className="flex flex-wrap gap-2">
                {journey.relatedDocumentTypes.map((documentType) => (
                  <Badge key={documentType} className="border-border bg-transparent">
                    {documentType}
                  </Badge>
                ))}
              </div>
              <ul className="space-y-2">
                {journey.recommendedActions.slice(0, 2).map((action) => (
                  <li key={action}>• {action}</li>
                ))}
              </ul>
              <p>
                {journey.linkedRecords.length} linked record{journey.linkedRecords.length === 1 ? '' : 's'} currently back this journey.
              </p>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  )
}

function MetricCard(props: {
  title: string
  value: string
  description: string
}) {
  return (
    <Card className="border-border/70 bg-card/95 shadow-sm">
      <CardHeader>
        <CardDescription>{props.title}</CardDescription>
        <CardTitle className="text-3xl">{props.value}</CardTitle>
      </CardHeader>
      <CardContent>
        <p className="text-sm text-muted-foreground">{props.description}</p>
      </CardContent>
    </Card>
  )
}
