import { JSX, SVGProps } from "react"

const Logo = (props: JSX.IntrinsicAttributes & SVGProps<SVGSVGElement>) => (
  <svg viewBox="0 0 32 32" fill="none" aria-hidden="true" {...props}>
    <path d="M16 2v8M16 22v8M2 16h8M22 16h8M6.1 6.1l5.65 5.65M20.25 20.25l5.65 5.65M25.9 6.1l-5.65 5.65M11.75 20.25 6.1 25.9" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" />
    <path d="M16 10.5 21.5 16 16 21.5 10.5 16 16 10.5Z" fill="currentColor" />
  </svg>
)
export default Logo
