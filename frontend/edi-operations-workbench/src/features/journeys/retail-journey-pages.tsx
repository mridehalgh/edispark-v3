import { Link, useParams } from 'react-router-dom'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { EmptyState, ErrorState, LoadingState, PlannedCapabilityState } from '@/components/workbench-states'
import { useDocumentSetCatalogue } from '@/features/document-sets/use-document-set-catalogue'
import { getRetailJourney, projectRetailJourneys, type JourneyStepViewModel } from '@/features/journeys/retail-journeys'
import { useIntegration } from '@/integration/integration-provider'

export function RetailJourneysPage() {
  const { contractState, refreshContract } = useIntegration()
  const { refresh, requestState } = useDocumentSetCatalogue()

  if (contractState.kind === 'loading-contract') {
    return <LoadingState title="Loading retail journeys" description="Connecting to the live backend before journey summaries render." />
  }

  if (contractState.kind === 'contract-failed') {
    return (
      <ErrorState
        title="Retail journeys are offline"
        description="The workbench needs the live OpenAPI contract before it can shape journey guidance."
        details={contractState.reason}
        onRetry={() => void refreshContract()}
        retryLabel="Retry connection"
      />
    )
  }

  if (requestState.status === 'pending' || requestState.status === 'idle') {
    return <LoadingState title="Curating UK retail journeys" description="Fetching live document-set evidence for the journey catalogue." />
  }

  if (requestState.status === 'failed') {
    return (
      <ErrorState
        title="Journey evidence could not be loaded"
        description="The document-set catalogue request failed before the journey catalogue could be projected."
        details={requestState.reason}
        onRetry={() => void refresh()}
      />
    )
  }

  const documentSetPage = requestState.data
  const journeys = projectRetailJourneys(documentSetPage.items)

  return (
    <div className="grid gap-4 xl:grid-cols-2">
      {journeys.map((journey) => (
        <Card key={journey.key} className="border-border/70 bg-card/95 shadow-sm">
          <CardHeader className="space-y-4">
            <Badge className="w-fit">{journey.title}</Badge>
            <div>
              <CardTitle>{journey.headline}</CardTitle>
              <CardDescription>{journey.summary}</CardDescription>
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
              {journey.recommendedActions.map((action) => (
                <li key={action}>• {action}</li>
              ))}
            </ul>
            <div className="flex items-center justify-between">
              <span>{journey.linkedRecords.length} linked record{journey.linkedRecords.length === 1 ? '' : 's'}</span>
              <Button asChild variant="outline">
                <Link to={`/retail-journeys/${journey.key}`}>Open detail view</Link>
              </Button>
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  )
}

export function RetailJourneyDetailPage() {
  const params = useParams<{ journeyKey: string }>()
  const { contractState, refreshContract } = useIntegration()
  const { refresh, requestState } = useDocumentSetCatalogue()

  if (contractState.kind === 'loading-contract') {
    return <LoadingState title="Loading journey detail" description="Connecting to the live backend before step guidance renders." />
  }

  if (contractState.kind === 'contract-failed') {
    return (
      <ErrorState
        title="Journey detail is unavailable"
        description="The workbench needs a reachable local backend contract before it can render the selected journey."
        details={contractState.reason}
        onRetry={() => void refreshContract()}
        retryLabel="Retry connection"
      />
    )
  }

  if (requestState.status === 'pending' || requestState.status === 'idle') {
    return <LoadingState title="Loading journey evidence" description="Fetching linked API-backed records for this retail journey." />
  }

  if (requestState.status === 'failed') {
    return (
      <ErrorState
        title="Journey evidence could not be loaded"
        description="The document-set request failed before the selected journey could be projected."
        details={requestState.reason}
        onRetry={() => void refresh()}
      />
    )
  }

  const documentSetPage = requestState.data
  const journey = getRetailJourney(params.journeyKey ?? '', documentSetPage.items)

  if (!journey) {
    return (
      <ErrorState
        title="Unknown retail journey"
        description="Choose one of the supported UK retail journeys from the catalogue."
        details={`Journey key "${params.journeyKey ?? 'unknown'}" is not configured.`}
      />
    )
  }

  return (
    <div className="space-y-6">
      <Card className="border-border/70 bg-card/95 shadow-sm">
        <CardHeader className="space-y-4">
          <Badge className="w-fit">{journey.title}</Badge>
          <div>
            <CardTitle>{journey.headline}</CardTitle>
            <CardDescription>{journey.summary}</CardDescription>
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
            {journey.recommendedActions.map((action) => (
              <li key={action}>• {action}</li>
            ))}
          </ul>
        </CardContent>
      </Card>

      <div className="grid gap-4 xl:grid-cols-3">
        {journey.steps.map((step) => (
          <JourneyStepCard key={step.key} step={step} />
        ))}
      </div>

      {journey.linkedRecords.length === 0 ? (
        <EmptyState
          title={`No ${journey.title} records yet`}
          description={journey.emptyDescription}
          action={
            <Button asChild variant="outline">
              <Link to="/document-sets">Open document-set area</Link>
            </Button>
          }
        />
      ) : (
        <Card className="border-border/70 bg-card/95 shadow-sm">
          <CardHeader>
            <CardTitle>Linked API-backed records</CardTitle>
            <CardDescription>Live document-set summaries currently informing this journey.</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
            {journey.linkedRecords.map((record) => (
              <div key={record.documentSetId} className="rounded-2xl border bg-background/70 p-4 text-sm text-muted-foreground">
                <p className="font-medium text-foreground">{record.documentSetId}</p>
                <p>Created by {record.createdBy}</p>
                <p>{new Date(record.createdAt).toLocaleString()}</p>
                <p>{record.documentCount} linked document{record.documentCount === 1 ? '' : 's'}</p>
                <div className="mt-3 flex flex-wrap gap-2">
                  {record.matchedDocumentTypes.map((documentType) => (
                    <Badge key={`${record.documentSetId}-${documentType}`} className="border-border bg-transparent">
                      {documentType}
                    </Badge>
                  ))}
                </div>
              </div>
            ))}
          </CardContent>
        </Card>
      )}
    </div>
  )
}

function JourneyStepCard(props: {
  step: JourneyStepViewModel
}) {
  if (props.step.state === 'planned') {
    return (
      <PlannedCapabilityState
        title={props.step.label}
        description={props.step.description}
        actionLabel={`${props.step.linkedRecordCount} supporting record${props.step.linkedRecordCount === 1 ? '' : 's'}`}
        action={
          <div className="flex flex-wrap gap-2">
            {props.step.relatedDocumentTypes.map((documentType) => (
              <Badge key={`${props.step.key}-${documentType}`} className="border-border bg-transparent">
                {documentType}
              </Badge>
            ))}
          </div>
        }
      />
    )
  }

  return (
    <Card className="border-border/70 bg-card/95 shadow-sm">
      <CardHeader className="space-y-4">
        <div className="flex items-start justify-between gap-3">
          <div>
            <CardTitle>{props.step.label}</CardTitle>
            <CardDescription>{props.step.description}</CardDescription>
          </div>
          <Badge
            className={
              props.step.state === 'available'
                ? 'border-transparent bg-primary text-primary-foreground'
                : 'border-transparent bg-secondary text-secondary-foreground'
            }
          >
            {props.step.state === 'available' ? 'Available' : 'Empty'}
          </Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-4 text-sm text-muted-foreground">
        <p>
          {props.step.linkedRecordCount} linked record{props.step.linkedRecordCount === 1 ? '' : 's'}
        </p>
        <div className="flex flex-wrap gap-2">
          {props.step.relatedDocumentTypes.map((documentType) => (
            <Badge key={`${props.step.key}-${documentType}`} className="border-border bg-transparent">
              {documentType}
            </Badge>
          ))}
        </div>
        {props.step.actionPath ? (
          <Button asChild size="sm" variant="outline">
            <Link to={props.step.actionPath}>{props.step.state === 'available' ? 'Open workflow area' : 'Prepare supporting records'}</Link>
          </Button>
        ) : null}
      </CardContent>
    </Card>
  )
}
