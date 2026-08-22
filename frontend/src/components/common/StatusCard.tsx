interface StatusCardProps {
  title: string
  description: string
}

export function StatusCard({ title, description }: StatusCardProps) {
  return (
    <article className="status-card">
      <h2>{title}</h2>
      <p>{description}</p>
    </article>
  )
}
