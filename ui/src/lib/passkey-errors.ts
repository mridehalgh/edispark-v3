type PasskeyError = {
  code?: string
  message?: string
}

const passkeyMessages: Record<string, string> = {
  AUTH_CANCELLED: "The passkey prompt was closed. Try again when you’re ready.",
  REGISTRATION_CANCELLED: "Passkey setup was cancelled. No changes were made.",
  PASSKEY_NOT_FOUND: "We couldn’t find a passkey for EDI Spark on this device. Try another device or sign in with your email and password.",
  AUTHENTICATION_FAILED: "We couldn’t verify that passkey. Try again or use your email and password.",
  CHALLENGE_NOT_FOUND: "This passkey request expired. Start again to get a new request.",
  PREVIOUSLY_REGISTERED: "That passkey is already connected to your EDI Spark account.",
  FAILED_TO_VERIFY_REGISTRATION: "We couldn’t verify this passkey. Check that your browser and device support passkeys, then try again.",
  YOU_ARE_NOT_ALLOWED_TO_REGISTER_THIS_PASSKEY: "This passkey can’t be added to your account. Sign in again and retry.",
  SESSION_REQUIRED: "Your session expired before the passkey could be added. Sign in again and retry.",
  AUTHENTICATOR_NOT_SUPPORTED: "This browser or device doesn’t support passkeys. Use a supported browser or another sign-in method.",
  UNKNOWN_ERROR: "Something interrupted the passkey request. Try again or use another sign-in method.",
}

export function getPasskeyError(error: unknown, action: "sign-in" | "registration") {
  const value = error && typeof error === "object" ? error as PasskeyError : null
  const code = value?.code?.toUpperCase()
  if (code && passkeyMessages[code]) return passkeyMessages[code]

  const message = value?.message?.toLowerCase() ?? ""
  if (message.includes("not found") || message.includes("no passkey")) {
    return passkeyMessages.PASSKEY_NOT_FOUND
  }
  if (message.includes("cancel") || message.includes("abort") || message.includes("notallowederror")) {
    return action === "registration" ? passkeyMessages.REGISTRATION_CANCELLED : passkeyMessages.AUTH_CANCELLED
  }
  if (message.includes("timed out") || message.includes("expired") || message.includes("challenge")) {
    return passkeyMessages.CHALLENGE_NOT_FOUND
  }
  if (message.includes("not supported") || message.includes("secure context")) {
    return passkeyMessages.AUTHENTICATOR_NOT_SUPPORTED
  }
  if (message.includes("already") || message.includes("previously registered")) {
    return passkeyMessages.PREVIOUSLY_REGISTERED
  }

  return action === "registration"
    ? "We couldn’t add that passkey. Check that your browser and device support passkeys, then try again."
    : "We couldn’t sign you in with that passkey. Try again or use your email and password."
}
