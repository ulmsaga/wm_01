function InputField(props) {
  return (
    <div className="form-row">
      <label className="form-label" htmlFor={props.id}>
        {props.label}
      </label>
      <input
        id={props.id}
        name={props.name}
        type={props.type || 'text'}
        value={props.value}
        placeholder={props.placeholder || ''}
        onChange={props.onChange}
        autoComplete={props.autoComplete || 'off'}
        className="form-input"
      />
    </div>
  );
}

export default InputField;