/*
 * Copyright (c) 2010 SimpleServer authors (see CONTRIBUTORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package simpleserver.bot;

import static simpleserver.util.Util.print;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.locks.ReentrantLock;

import simpleserver.Coordinate.Dimension;
import simpleserver.Main;
import simpleserver.Position;
import simpleserver.Server;
import simpleserver.stream.Encryption.ServerEncryption;

public class Bot {
  protected String name;
  protected Server server;
  private boolean connected;
  private boolean expectDisconnect;
  protected boolean ready;
  protected boolean dead;
  protected int playerEntityId;

  private Socket socket;
  protected DataInputStream in;
  protected DataOutputStream out;

  ReentrantLock writeLock;
  protected Position position;
  protected BotController controller;
  protected boolean gotFirstPacket = false;
  private byte lastPacket;
  private float health;

  private ServerEncryption encryption = new ServerEncryption();

  public Bot(Server server, String name) {
    this.name = name;
    this.server = server;
    position = new Position();
  }

  void connect() throws UnknownHostException, IOException {

    try {
      InetAddress localAddress = InetAddress.getByName(Server.addressFactory.getNextAddress());
      socket = new Socket(InetAddress.getByName(null), server.options.getInt("internalPort"), localAddress, 0);
    } catch (Exception e) {
      socket = new Socket(InetAddress.getByName(null), server.options.getInt("internalPort"));
    }
    in = new DataInputStream(socket.getInputStream());
    out = new DataOutputStream(socket.getOutputStream());

    writeLock = new ReentrantLock();

    connected = true;
    new Tunneler().start();

    handshake();
  }

  boolean ninja() {
    return false;
  }

  protected void positionUpdate() throws IOException {
  }

  private void keepAlive(int keepAliveId) throws IOException {
    writeLock.lock();
    out.writeByte(0x0);
    out.writeInt(keepAliveId);
    writeLock.unlock();
  }

  private void handshake() throws IOException {
    writeLock.lock();
    out.writeByte(2);
    out.writeByte(Main.protocolVersion);
    write(name);
    write("localhost");
    out.writeInt(server.options.getInt("internalPort"));
    out.flush();
    writeLock.unlock();
  }

  public void logout() throws IOException {
    die();
    expectDisconnect = true;
    out.writeByte(0xff);
    write("quitting");
    out.flush();
  }

  protected void login() throws IOException {
    writeLock.lock();
    out.writeByte(0xcd);
    out.writeByte(0);
    writeLock.unlock();
  }

  private void sendSharedKey() throws IOException {
    writeLock.lock();
    out.writeByte(0xfc);
    byte[] key = encryption.getEncryptedSharedKey();
    out.writeShort(key.length);
    out.write(key);
    byte[] challengeTokenResponse = encryption.encryptChallengeToken();
    out.writeShort(challengeTokenResponse.length);
    out.write(challengeTokenResponse);
    out.flush();
    writeLock.unlock();
  }

  private void respawn() throws IOException {
    writeLock.lock();
    out.writeByte(0xcd);
    out.writeByte(1);
    writeLock.unlock();
  }

  protected void ready() throws IOException {
    ready = true;
  }

  protected void walk(double d) {
    double heading = position.yaw * Math.PI / 180;
    position.x -= Math.sin(heading) * d;
    position.z += Math.cos(heading) * d;
  }

  protected void ascend(double d) {
    position.y += d;
    position.stance += d;

    if (position.stance - position.y > 1.6 || position.stance - position.y < 0.15) {
      position.stance = position.y + 0.5;
    }
  }

  protected void sendPosition() throws IOException {
    writeLock.lock();
    position.send(out);
    writeLock.unlock();
  }

  protected boolean trashdat() {
    return true;
  }

  protected void handlePacket(byte packetId) throws IOException {
    // System.out.println("Packet: 0x" + Integer.toHexString(packetId));
    switch (packetId) {
      case 0x01: // Login Request
        int eid = in.readInt();
        if (playerEntityId == 0) {
          playerEntityId = eid;
        }

        readUTF16();
        in.readByte();
        position.dimension = Dimension.get(in.readByte());
        in.readByte();
        in.readByte();
        in.readByte();
        break;
      case 0x0d: // Player Position & Look
        double x = in.readDouble();
        double stance = in.readDouble();
        double y = in.readDouble();
        double z = in.readDouble();
        float yaw = in.readFloat();
        float pitch = in.readFloat();
        boolean onGround = in.readBoolean();
        position.updatePosition(x, y, z, stance);
        position.updateLook(yaw, pitch);
        position.updateGround(onGround);
        if (!ready) {
          sendPosition();
          ready();
        } else if (dead) {
          sendPosition();
          dead = false;
        }
        positionUpdate();
        break;
      case 0x0b: // Player Position
        double x2 = in.readDouble();
        double stance2 = in.readDouble();
        double y2 = in.readDouble();
        double z2 = in.readDouble();
        boolean onGround2 = in.readBoolean();
        position.updatePosition(x2, y2, z2, stance2);
        position.updateGround(onGround2);
        positionUpdate();
        break;
      case (byte) 0xff: // Disconnect/Kick
        String reason = readUTF16();
        error(reason);
        break;
      case 0x00: // Keep Alive
        keepAlive(in.readInt());
        break;
      case 0x03: // Chat Message
        readUTF16();
        break;
      case 0x04: // Time Update
        in.readLong();
        in.readLong();
        break;
      case 0x05: // Entity Equipment
        in.readInt();
        in.readShort();
        readItem();
        break;
      case 0x06: // Spawn Position
        readNBytes(12);
        break;
      case 0x07: // Use Entity
        in.readInt();
        in.readInt();
        in.readBoolean();
        break;
      case 0x08: // Update Health
        health = in.readFloat();
        in.readShort();
        in.readFloat();
        if (health <= 0) {
          dead = true;
          respawn();
        }
        break;
      case 0x09: // Respawn
        position.dimension = Dimension.get((byte) in.readInt());
        in.readByte();
        in.readByte();
        in.readShort();
        readUTF16();
        break;
      case 0x0a: // Player
        in.readBoolean();
        break;
      case 0x0c: // Player Look
        readNBytes(9);
        break;
      case 0x0e: // Player Digging
        in.readByte();
        in.readInt();
        in.readByte();
        in.readInt();
        in.readByte();
        break;
      case 0x0f: // Player Block Placement
        in.readInt();
        in.readByte();
        in.readInt();
        in.readByte();
        readItem();
        in.readByte();
        in.readByte();
        in.readByte();
        break;
      case 0x10: // Holding Change
        in.readShort();
        break;
      case 0x11: // Use Bed
        readNBytes(14);
        break;
      case 0x12: // Animation
        readNBytes(5);
        break;
      case 0x13: // Entity Action
        in.readInt();
        in.readByte();
        in.readInt();
        break;
      case 0x14: // Named Entity Spawn
        in.readInt();
        readUTF16();
        readNBytes(16);
        readUnknownBlob();
        break;
      case 0x16: // Collect Item
        readNBytes(8);
        break;
      case 0x17: // Add Object/Vehicle
        in.readInt();
        in.readByte();
        in.readInt();
        in.readInt();
        in.readInt();
        in.readByte();
        in.readByte();
        int flag = in.readInt();
        if (flag > 0) {
          in.readShort();
          in.readShort();
          in.readShort();
        }
        break;
      case 0x18: // Mob Spawn
        in.readInt();
        in.readByte();
        in.readInt();
        in.readInt();
        in.readInt();
        in.readByte();
        in.readByte();
        in.readByte();
        in.readShort();
        in.readShort();
        in.readShort();
        readUnknownBlob();
        break;
      case 0x19: // Entity: Painting
        in.readInt();
        readUTF16();
        in.readInt();
        in.readInt();
        in.readInt();
        in.readInt();
        break;
      case 0x1a: // Experience Orb
        in.readInt();
        in.readInt();
        in.readInt();
        in.readInt();
        in.readShort();
        break;
      case 0x1b: // Steer Vehicle
        in.readFloat();
        in.readFloat();
        in.readBoolean();
        in.readBoolean();
        break;
      case 0x1c: // Entity Velocity
        readNBytes(10);
        break;
      case 0x1d: // Destroy Entity
        byte destroyCount = in.readByte();
        if (destroyCount > 0) {
          readNBytes(destroyCount * 4);
        }
        break;
      case 0x1e: // Entity
        readNBytes(4);
        break;
      case 0x1f: // Entity Relative Move
        readNBytes(7);
        break;
      case 0x20: // Entity Look
        readNBytes(6);
        break;
      case 0x21: // Entity Look and Relative Move
        readNBytes(9);
        break;
      case 0x22: // Entity Teleport
        readNBytes(18);
        break;
      case 0x23: // ???, added in 12w03a
        in.readInt();
        in.readByte();
        break;
      case 0x26: // Entity Status
        readNBytes(5);
        break;
      case 0x27: // Attach Entity
        in.readInt();
        in.readInt();
        in.readBoolean();
        break;
      case 0x28: // Entity Metadata
        in.readInt();
        readUnknownBlob();
        break;
      case 0x29: // Entity Effect
        in.readInt();
        in.readByte();
        in.readByte();
        in.readShort();
        break;
      case 0x2a: // Remove Entity Effect
        in.readInt();
        in.readByte();
        break;
      case 0x2b: // Experience
        in.readFloat();
        in.readShort();
        in.readShort();
        break;
      case 0x2c: // Entity Properties
        in.readInt();
        int properties_count = in.readInt();
        short list_length = 0;

        // loop for every property key/value pair
        for (int i = 0; i < properties_count; i++) {
          readUTF16();
          in.readDouble();
          list_length = in.readShort();

          // loop for list_length
          if (list_length > 0) {
            for (int k = 0; k < list_length; k++) {
              in.readLong();
              in.readLong();
              in.readDouble();
              in.readByte();
            }
          }
        }
        break;
      case 0x33: // Map Chunk
        readNBytes(13);
        readNBytes(in.readInt());
        break;
      case 0x34: // Multi Block Change
        in.readInt();
        in.readInt();
        in.readShort();
        readNBytes(in.readInt());
        break;
      case 0x35: // Block Change
        in.readInt();
        in.readByte();
        in.readInt();
        in.readShort();
        in.readByte();
        break;
      case 0x36: // Block Action
        readNBytes(13);
        break;
      case 0x37: // Mining progress
        in.readInt();
        in.readInt();
        in.readInt();
        in.readInt();
        in.readByte();
        break;
      case 0x38: // Chunk Bulk
        readNBytes(in.readShort() * 12 + in.readInt());
        short chunkCount = in.readShort();
        int dataLength = in.readInt();
        in.readBoolean();
        readNBytes(chunkCount * 12 + dataLength);
        break;
      case 0x3c: // Explosion
        readNBytes(28);
        int recordCount = in.readInt();
        readNBytes(recordCount * 3);
        readNBytes(12);
        break;
      case 0x3d: // Sound/Particle Effect
        in.readInt();
        in.readInt();
        in.readByte();
        in.readInt();
        in.readInt();
        in.readBoolean();
        break;
      case 0x3e: // Named Sound Effect
        readUTF16();
        in.readInt();
        in.readInt();
        in.readInt();
        in.readFloat();
        in.readByte();
        break;
      case 0x46: // New/Invalid State
        readNBytes(2);
        break;
      case 0x47: // Thunderbolt
        readNBytes(17);
        break;
      case 0x64: // Open Window
        in.readByte();
        byte invtype = in.readByte();
        readUTF16();
        in.readByte();
        in.readBoolean();
        if (invtype == 11) {
          in.readInt();
        }
        break;
      case 0x65: // Close Window
        in.readByte();
        break;
      case 0x66: // Window Click
        in.readByte();
        in.readShort();
        in.readByte();
        in.readShort();
        in.readByte();
        readItem();
        break;
      case 0x67: // Set Slot
        in.readByte();
        in.readShort();
        readItem();
        break;
      case 0x68: // Window Items
        in.readByte();
        short count = in.readShort();
        for (int c = 0; c < count; ++c) {
          readItem();
        }
        break;
      case 0x69: // Update Window Property
        in.readByte();
        in.readShort();
        in.readShort();
        break;
      case 0x6a: // Transaction
        in.readByte();
        in.readShort();
        in.readBoolean();
        break;
      case 0x6b: // Creative Inventory Action
        in.readShort();
        readItem();
        break;
      case (byte) 0x6c: // Enchant Item
        readNBytes(2);
        break;
      case (byte) 0x82: // Update Sign
        in.readInt();
        in.readShort();
        in.readInt();
        readUTF16();
        readUTF16();
        readUTF16();
        readUTF16();
        break;
      case (byte) 0x83: // Item Data
        in.readShort();
        in.readShort();
        short length = in.readShort();
        readNBytes(length);
        break;
      case (byte) 0x84: // added in 12w06a
        in.readInt();
        in.readShort();
        in.readInt();
        in.readByte();
        short nbtLenght = in.readShort();
        if (nbtLenght > 0) {
          readNBytes(nbtLenght);
        }
        break;
      case (byte) 0x85: // Sign Placement
        in.readByte();
        in.readInt();
        in.readInt();
        in.readInt();
        break;
      case (byte) 0xc8: // Increment Statistic
        in.readInt();
        in.readInt();
        break;
      case (byte) 0xc9: // Player List Item
        readUTF16();
        in.readBoolean();
        in.readShort();
        break;
      case (byte) 0xca: // Player Abilities
        in.readByte();
        in.readFloat();
        in.readFloat();
        break;
      case (byte) 0xcb: // Tab-Completion
        readUTF16();
        break;
      case (byte) 0xcc: // Locale and View Distance
        readUTF16();
        in.readByte();
        in.readByte();
        in.readByte();
        in.readBoolean();
        break;
      case (byte) 0xcd: // Login & Respawn
        in.readByte();
        break;
      case (byte) 0xce: // create scoreboard
        readUTF16();
        readUTF16();
        in.readByte();
        break;
      case (byte) 0xcf: // update score
        readUTF16();
        byte updateRemove = in.readByte();
        if (updateRemove == 0) {
          readUTF16();
          in.readInt();
        }
        break;
      case (byte) 0xd0: // display scoreboard
        in.readByte();
        readUTF16();
        break;
      case (byte) 0xd1: // teams
        readUTF16();
        byte mode = in.readByte();
        short playerCount = -1;

        if (mode == 2 || mode == 0) {
          readUTF16(); // team display name
          readUTF16(); // team prefix
          readUTF16(); // team suffix
          in.readByte(); // friendly fire
        }

        // only ran if 0,3,4
        if (mode == 0 || mode == 3 || mode == 4) {
          playerCount = in.readShort();

          if (playerCount != -1) {
            for (int i = 0; i < playerCount; i++) {
              readUTF16();
            }
          }
        }
        break;
      case (byte) 0xe6: // ModLoaderMP by SDK
        in.readInt(); // mod
        in.readInt(); // packet id
        readNBytes(in.readInt() * 4); // ints
        readNBytes(in.readInt() * 4); // floats
        int sizeString = in.readInt(); // strings
        for (int i = 0; i < sizeString; i++) {
          readNBytes(in.readInt());
        }
        break;
      case (byte) 0xfa: // Plugin Message
        readUTF16();
        short arrayLength = in.readShort();
        readNBytes(0xff & arrayLength);
        break;
      case (byte) 0xfc: // Encryption Key Response
        byte[] sharedKey = new byte[in.readShort()];
        in.readFully(sharedKey);
        byte[] challengeTokenResponse = new byte[in.readShort()];
        in.readFully(challengeTokenResponse);
        in = new DataInputStream(new BufferedInputStream(encryption.encryptedInputStream(socket.getInputStream())));
        out = new DataOutputStream(new BufferedOutputStream(encryption.encryptedOutputStream(socket.getOutputStream())));
        login();
        break;
      case (byte) 0xfd: // Encryption Key Request (server -> client)
        readUTF16();
        byte[] keyBytes = new byte[in.readShort()];
        in.readFully(keyBytes);
        byte[] challengeToken = new byte[in.readShort()];
        in.readFully(challengeToken);
        encryption.setPublicKey(keyBytes);
        encryption.setChallengeToken(challengeToken);
        sendSharedKey();
        break;
      case (byte) 0xfe: // Server List Ping
        break;
      default:
        error("Unable to handle packet 0x" + Integer.toHexString(packetId)
            + " after 0x" + Integer.toHexString(lastPacket));
    }
    lastPacket = packetId;
  }

  private void readItem() throws IOException {
    if (in.readShort() > 0) {
      in.readByte();
      in.readShort();
      short length;
      if ((length = in.readShort()) > 0) {
        readNBytes(length);
      }
    }
  }

  private void readUnknownBlob() throws IOException {
    byte unknown = in.readByte();

    while (unknown != 0x7f) {
      int type = (unknown & 0xE0) >> 5;

      switch (type) {
        case 0:
          in.readByte();
          break;
        case 1:
          in.readShort();
          break;
        case 2:
          in.readInt();
          break;
        case 3:
          in.readFloat();
          break;
        case 4:
          readUTF16();
          break;
        case 5:
          readItem();
          break;
        case 6:
          in.readInt();
          in.readInt();
          in.readInt();
      }
      unknown = in.readByte();
    }
  }

  protected String write(String s) throws IOException {
    byte[] bytes = s.getBytes("UTF-16");
    if (s.length() == 0) {
      out.write((byte) 0x00);
      out.write((byte) 0x00);
      return s;
    }
    bytes[0] = (byte) ((s.length() >> 8) & 0xFF);
    bytes[1] = (byte) ((s.length() & 0xFF));
    for (byte b : bytes) {
      out.write(b);
    }
    return s;
  }

  protected String readUTF16() throws IOException {
    short length = in.readShort();
    byte[] bytes = new byte[length * 2 + 2];
    for (short i = 0; i < length * 2; i++) {
      bytes[i + 2] = in.readByte();
    }
    bytes[0] = (byte) 0xfffffffe;
    bytes[1] = (byte) 0xffffffff;
    return new String(bytes, "UTF-16");
  }

  private void readNBytes(int bytes) throws IOException {
    for (int c = 0; c < bytes; ++c) {
      in.readByte();
    }
  }

  protected void die() {
    connected = false;
    if (controller != null) {
      controller.remove(this);
    }
    if (trashdat()) {
      File dat = server.getPlayerFile(name);
      if (controller != null) {
        controller.trash(dat);
      } else {
        dat.delete();
      }
    }
  }

  protected void error(String reason) {
    die();
    if (!expectDisconnect) {
      print("Bot " + name + " died (" + reason + ")\n");
    }
  }

  public void setController(BotController controller) {
    this.controller = controller;
  }

  private final class Tunneler extends Thread {
    @Override
    public void run() {
      while (connected) {
        try {
          handlePacket(in.readByte());
          out.flush();
          if (!gotFirstPacket) {
            gotFirstPacket = true;
          }
        } catch (IOException e) {
          if (!gotFirstPacket) {
            try {
              connect();
            } catch (Exception e2) {
              error("Socket closed on reconnect");
            }
            break;
          } else {
            error("Socket closed");
          }
        }
      }
    }
  }
}
