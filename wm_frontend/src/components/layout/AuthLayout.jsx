function AuthLayout(props) {
  return (
    <div className="min-h-screen w-full bg-muted dark:bg-slate-950 transition-colors">
      {props.children}
    </div>
  );
}

export default AuthLayout;