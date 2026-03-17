import Avatar from '../ui/Avatar.jsx';

function TopBar({ title = 'Admin Panel', userName = 'Admin User' }) {
  return (
    <header>
      <div>
        <h1>{title}</h1>
      </div>
      <div>
        <Avatar name={userName} />
      </div>
    </header>
  );
}

export default TopBar;
