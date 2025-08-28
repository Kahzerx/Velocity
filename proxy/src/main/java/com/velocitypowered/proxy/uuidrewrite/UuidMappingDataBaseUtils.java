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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * [fallen's fork] player uuid rewrite - uuid database utils.
 */
public class UuidMappingDataBaseUtils {
  private static final Logger logger = LogManager.getLogger(UuidMappingDataBaseUtils.class);

  /**
   * Serialize the given GameProfile.
   */
  public static byte[] serializeGameProfile(GameProfile profile) {
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

      return profileBuf;
    } catch (Exception e) {
      logger.error("Failed to serialize and encrypt GameProfile", e);
      return null;
    }
  }

  /**
   * Deserialize a GameProfile.
   */
  @Nullable
  public static GameProfile deserializeGameProfile(byte[] profileBuf) {
    if (profileBuf == null || profileBuf.length == 0) {
      return null;
    }
    try {
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
}
