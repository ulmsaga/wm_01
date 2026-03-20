import type { InputHTMLAttributes } from 'react';

interface InputFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
}

function InputField({ id, name, type = 'text', value, placeholder = '', onChange, autoComplete = 'off', label }: InputFieldProps) {
  return (
    <div className="form-row">
      <label className="form-label" htmlFor={id}>
        {label}
      </label>
      <input
        id={id}
        name={name}
        type={type}
        value={value}
        placeholder={placeholder}
        onChange={onChange}
        autoComplete={autoComplete}
        className="form-input"
      />
    </div>
  );
}

export default InputField;
