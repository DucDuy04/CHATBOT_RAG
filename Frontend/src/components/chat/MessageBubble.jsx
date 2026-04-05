function MessageBubble({ role = 'assistant', content = '' }) {
  return (
    <article data-role={role}>
      <strong>{role}</strong>
      <p>{content || 'Empty message'}</p>
    </article>
  );
}

export default MessageBubble;
