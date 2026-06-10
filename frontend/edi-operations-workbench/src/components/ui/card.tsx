import * as React from 'react'

import { classNames } from '@/lib/class-names'

const Card = React.forwardRef<HTMLDivElement, React.HTMLAttributes<HTMLDivElement>>(function Card(
  { className, ...props },
  ref
) {
  return <div ref={ref} className={classNames('rounded-xl border bg-card text-card-foreground shadow-sm', className)} {...props} />
})

const CardHeader = React.forwardRef<HTMLDivElement, React.HTMLAttributes<HTMLDivElement>>(function CardHeader(
  { className, ...props },
  ref
) {
  return <div ref={ref} className={classNames('flex flex-col space-y-1.5 p-6', className)} {...props} />
})

const CardTitle = React.forwardRef<HTMLParagraphElement, React.HTMLAttributes<HTMLHeadingElement>>(function CardTitle(
  { className, ...props },
  ref
) {
  return <h3 ref={ref} className={classNames('text-2xl font-semibold leading-none tracking-tight', className)} {...props} />
})

const CardDescription = React.forwardRef<HTMLParagraphElement, React.HTMLAttributes<HTMLParagraphElement>>(
  function CardDescription({ className, ...props }, ref) {
    return <p ref={ref} className={classNames('text-sm text-muted-foreground', className)} {...props} />
  }
)

const CardContent = React.forwardRef<HTMLDivElement, React.HTMLAttributes<HTMLDivElement>>(function CardContent(
  { className, ...props },
  ref
) {
  return <div ref={ref} className={classNames('p-6 pt-0', className)} {...props} />
})

export { Card, CardContent, CardDescription, CardHeader, CardTitle }
