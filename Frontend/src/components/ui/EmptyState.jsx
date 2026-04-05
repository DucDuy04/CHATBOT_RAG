function EmptyState({ title = 'No data', description = 'There is nothing to display yet.' }) {
  return (
    <div>
      <h3>{title}</h3>
      <p>{description}</p>
    </div>
  );
}

export default EmptyState;
