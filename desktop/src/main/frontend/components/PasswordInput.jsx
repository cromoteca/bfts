import React, { useState } from "react";

export default function PasswordInput({ 
  id, 
  label, 
  description, 
  value, 
  onChange, 
  placeholder, 
  required = false,
  autoFocus = false 
}) {
  const [isVisible, setIsVisible] = useState(false);

  const toggleVisibility = () => {
    setIsVisible(!isVisible);
  };

  return (
    <div className="form-group">
      <label htmlFor={id}>
        {label} {required && '*'}
        {description && <small>{description}</small>}
      </label>
      <div className="password-input-container">
        <input
          id={id}
          type={isVisible ? "text" : "password"}
          value={value}
          onChange={e => onChange(e.target.value)}
          placeholder={placeholder}
          className="input-large"
          autoFocus={autoFocus}
        />
        <button
          type="button"
          className="password-toggle"
          onClick={toggleVisibility}
          title={isVisible ? "Hide password" : "Show password"}
          aria-label={isVisible ? "Hide password" : "Show password"}
        >
          {isVisible ? "Hide" : "Show"}
        </button>
      </div>
    </div>
  );
}
