type TransactionalEmail = {
  to: string
  subject: string
  text: string
  html: string
}

export async function sendTransactionalEmail(email: TransactionalEmail) {
  const apiKey = process.env.RESEND_API_KEY
  const from = process.env.EMAIL_FROM

  if (!apiKey || !from) {
    if (process.env.NODE_ENV !== "production") {
      console.info("Transactional email not sent; configure RESEND_API_KEY and EMAIL_FROM.", email)
      return
    }
    throw new Error("Email delivery is not configured")
  }

  const response = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: { Authorization: `Bearer ${apiKey}`, "Content-Type": "application/json" },
    body: JSON.stringify({ from, ...email }),
  })

  if (!response.ok) throw new Error("Unable to deliver transactional email")
}
