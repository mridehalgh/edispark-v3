import * as React from 'react'

import { classNames } from '@/lib/class-names'

function Badge({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={classNames(
        'inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold transition-colors',
        className
      )}
      {...props}
    />
  )
}

export { Badge }
