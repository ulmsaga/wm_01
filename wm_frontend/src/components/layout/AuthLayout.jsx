function AuthLayout(props) {
  return (
    <div className="auth-layout">
      <div className="auth-box">
        <h1 className="auth-title">{props.title}</h1>
        <div className="auth-content">{props.children}</div>
      </div>
    </div>
  );
}

export default AuthLayout;