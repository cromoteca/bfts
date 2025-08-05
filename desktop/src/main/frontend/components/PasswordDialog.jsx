import React, { useState, useEffect } from "react";
import PasswordInput from "./PasswordInput.jsx";

export default function PasswordDialog({ 
  isOpen, 
  storageName, 
  encryptionType, 
  onConfirm, 
  onCancel 
}) {
  const [passwords, setPasswords] = useState({
    transmissionPassword: '',
    fileEncryptionPassword: ''
  });

  // Reset passwords when dialog opens/closes
  useEffect(() => {
    if (isOpen) {
      setPasswords({
        transmissionPassword: '',
        fileEncryptionPassword: ''
      });
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const handleOverlayClick = (e) => {
    if (e.target === e.currentTarget) {
      handleCancel();
    }
  };

  const handlePasswordChange = (field, value) => {
    setPasswords(prev => ({ ...prev, [field]: value }));
  };

  const isFormValid = () => {
    return passwords.transmissionPassword.trim() && 
           (encryptionType === 'NONE' || passwords.fileEncryptionPassword.trim());
  };

  const handleConfirm = () => {
    if (isFormValid()) {
      onConfirm(passwords);
    }
  };

  const handleCancel = () => {
    setPasswords({
      transmissionPassword: '',
      fileEncryptionPassword: ''
    });
    onCancel();
  };

  return (
    <div className="modal-overlay" onClick={handleOverlayClick}>
      <div className="modal-content" onClick={e => e.stopPropagation()}>
        <h3>Set Passwords for "{storageName}"</h3>
        
        <PasswordInput
          id="transmissionPassword"
          label="Transmission Password"
          description="Used to encrypt HTTP communication"
          value={passwords.transmissionPassword}
          onChange={value => handlePasswordChange('transmissionPassword', value)}
          placeholder="Enter transmission password"
          required
          autoFocus
        />

        {encryptionType !== 'NONE' && (
          <PasswordInput
            id="fileEncryptionPassword"
            label="File Encryption Password"
            description="Used to encrypt files before transmission (immutable once set)"
            value={passwords.fileEncryptionPassword}
            onChange={value => handlePasswordChange('fileEncryptionPassword', value)}
            placeholder="Enter file encryption password"
            required
          />
        )}

        <div className="modal-actions">
          <button 
            type="button" 
            className="btn-secondary" 
            onClick={handleCancel}
          >
            Cancel
          </button>
          <button 
            type="button" 
            className="btn-primary" 
            onClick={handleConfirm}
            disabled={!isFormValid()}
          >
            Add Storage
          </button>
        </div>
      </div>
    </div>
  );
}
