function Spinner({ label = 'Loading...' }) {
  return (
    <div role="status" aria-live="polite">
      <span>{label}</span>
    </div>
  );
}

export default Spinner;
