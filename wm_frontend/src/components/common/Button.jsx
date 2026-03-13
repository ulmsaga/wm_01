function Button(props) {
  return (
    <button
      type={props.type || 'button'}
      className="btn-primary"
      onClick={props.onClick}
      disabled={props.disabled}
    >
      {props.children}
    </button>
  );
}

export default Button;