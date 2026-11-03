import { ClientApplication } from "@/components/application/client-application"

export default function Page() {
  const localTools = process.env.NODE_ENV === "development"
    && process.env.LOCAL_TEST_USER_BOOTSTRAP === "true"
  return <ClientApplication localTools={localTools} />
}
