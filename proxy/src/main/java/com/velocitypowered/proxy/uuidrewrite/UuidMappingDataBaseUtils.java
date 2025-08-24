/*
 * Copyright (C) 2025 Velocity Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.velocitypowered.proxy.uuidrewrite;

import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * [fallen's fork] player uuid rewrite - uuid database utils.
 */
public class UuidMappingDataBaseUtils {
  private static final Logger logger = LogManager.getLogger(UuidMappingDataBaseUtils.class);

  private static final int GCM_NONCE_LENGTH = 12; // Recommended nonce length for GCM (in bytes)
  private static final int GCM_TAG_LENGTH = 16; // Authentication tag length (in bytes)

  /**
   * Serialize the given GameProfile with optional encryption key.
   */
  public static byte[] serializeGameProfile(GameProfile profile, String encryptionKey) {
    try {
      byte[] profileBuf;
      ByteBuf buf = Unpooled.buffer();
      try {
        ProtocolUtils.writeUuid(buf, profile.getId());
        ProtocolUtils.writeString(buf, profile.getName());
        ProtocolUtils.writeProperties(buf, profile.getProperties());

        profileBuf = new byte[buf.readableBytes()];
        buf.readBytes(profileBuf);
      } finally {
        buf.release();
      }

      if (!encryptionKey.isEmpty()) {
        profileBuf = encryptData(profileBuf, encryptionKey);
      }

      return profileBuf;
    } catch (Exception e) {
      logger.error("Failed to serialize and encrypt GameProfile", e);
      return null;
    }
  }

  /**
   * Deserialize a GameProfile with optional encryption key.
   */
  @Nullable
  public static GameProfile deserializeGameProfile(byte[] profileBuf, String encryptionKey) {
    if (profileBuf == null || profileBuf.length == 0) {
      return null;
    }
    try {
      if (!encryptionKey.isEmpty()) {
        profileBuf = decryptData(profileBuf, encryptionKey);
      }

      ByteBuf buf = Unpooled.wrappedBuffer(profileBuf);
      try {
        return new GameProfile(
                ProtocolUtils.readUuid(buf),
                ProtocolUtils.readString(buf),
                ProtocolUtils.readProperties(buf)
        );
      } finally {
        buf.release();
      }
    } catch (Exception e) {
      logger.error("Failed to decrypt and deserialize GameProfile: {}", e.toString());
      return null;
    }
  }

  private static byte[] encryptData(byte[] plaintext, String key) throws Exception {
    byte[] keyBytes = deriveKey(key);

    // Generate random nonce
    byte[] nonce = new byte[GCM_NONCE_LENGTH];
    SecureRandom random = new SecureRandom();
    random.nextBytes(nonce);

    // Initialize AES-256-GCM cipher
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
    GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, nonce);
    cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

    // Encrypt the plaintext
    byte[] ciphertext = cipher.doFinal(plaintext);

    // Concatenate nonce + ciphertext (including tag)
    byte[] result = new byte[nonce.length + ciphertext.length];
    System.arraycopy(nonce, 0, result, 0, nonce.length);
    System.arraycopy(ciphertext, 0, result, nonce.length, ciphertext.length);
    return result;
  }

  private static byte[] decryptData(byte[] encryptedData, String key) throws Exception {
    if (encryptedData == null || encryptedData.length < GCM_NONCE_LENGTH + GCM_TAG_LENGTH) {
      throw new IllegalArgumentException("Invalid encrypted data length");
    }

    byte[] keyBytes = deriveKey(key);

    // Extract nonce and ciphertext
    byte[] nonce = new byte[GCM_NONCE_LENGTH];
    byte[] ciphertext = new byte[encryptedData.length - GCM_NONCE_LENGTH];
    System.arraycopy(encryptedData, 0, nonce, 0, GCM_NONCE_LENGTH);
    System.arraycopy(encryptedData, GCM_NONCE_LENGTH, ciphertext, 0, ciphertext.length);

    // Initialize AES-256-GCM cipher for decryption
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
    GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, nonce);
    cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

    // Decrypt the ciphertext
    return cipher.doFinal(ciphertext);
  }

  private static final Object cacheLock = new Object();
  private static String cachedKey = null;
  private static byte[] cachedKeyBytes = null;

  private static byte[] deriveKey(String key) throws Exception {
    synchronized (cacheLock) {
      if (cachedKey != null && Objects.equals(key, cachedKey)) {
        return Objects.requireNonNull(cachedKeyBytes);
      }
    }

    final String salt = "velocity-uuid-rewrite";
    PBEKeySpec spec = new PBEKeySpec(key.toCharArray(), salt.getBytes(StandardCharsets.UTF_8), 100000, 256);
    SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
    byte[] result = skf.generateSecret(spec).getEncoded();
    if (result.length != 32) {
      throw new IllegalStateException("Expected 32-byte key, got " + result.length);
    }

    synchronized (cacheLock) {
      cachedKey = key;
      cachedKeyBytes = result;
    }
    return result;
  }
}
