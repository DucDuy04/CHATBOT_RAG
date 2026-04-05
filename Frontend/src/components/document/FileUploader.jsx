import Button from '../ui/Button.jsx';

function FileUploader({ accept = '*', onSelect, multiple = false }) {
  const handleChange = (event) => {
    const files = Array.from(event.target.files ?? []);
    onSelect?.(multiple ? files : files[0] ?? null);
  };

  return (
    <div>
      <input type="file" accept={accept} multiple={multiple} onChange={handleChange} />
      <Button type="button">Choose file</Button>
    </div>
  );
}

export default FileUploader;
