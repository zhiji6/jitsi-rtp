/*
 * Copyright @ 2018 - present 8x8, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jitsi.rtp.rtcp.rtcpfb

import io.kotlintest.IsolationMode
import io.kotlintest.matchers.collections.shouldContainInOrder
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.specs.ShouldSpec
import org.jitsi.rtp.extensions.subBuffer
import org.jitsi.rtp.rtcp.RtcpHeader
import org.jitsi.rtp.rtcp.RtcpIterator
import org.jitsi.rtp.rtcp.rtcpfb.fci.GenericNackBlp
import org.jitsi.rtp.util.byteBufferOf
import org.jitsi.test_helpers.matchers.haveSameContentAs
import java.nio.ByteBuffer

internal class RtcpFbNackPacketTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode? = IsolationMode.InstancePerLeaf

    private val samplePacketId = 0
    private val sampleBlp = GenericNackBlp(listOf(1, 3, 5, 7, 9, 11, 13, 15))
    private val sampleMediaSsrc = 12345L
    private val sampleHeader =
        RtcpHeader(length = 3, packetType = TransportLayerFbPacket.PT,
            reportCount = RtcpFbNackPacket.FMT)
    val sampleRtcpFbNackPacketBuf = ByteBuffer.allocate(16).apply {
        put(sampleHeader.getBuffer())
        putInt(sampleMediaSsrc.toInt())
        putShort(samplePacketId.toShort())
        put(sampleBlp.getBuffer())
    }.flip() as ByteBuffer

    val multipleNackBlocksBuf = byteBufferOf(
        0x81, 0xCD, 0x00, 0x04,
        0xA2, 0x78, 0xEB, 0x18,
        0x84, 0x50, 0x49, 0x6C,
        // packet id = 44832, other nacked packets = 44838, 44848
        0xAF, 0x20, 0x80, 0x20,
        // packet id = 44850, other nacked packets = 44854, 44856
        0xAF, 0x32, 0x00, 0x28
    )

    init {
        "Creating an RtcpFbNackPacket" {
            "from a buffer" {
                val nackPacket = RtcpFbNackPacket.fromBuffer(sampleRtcpFbNackPacketBuf)
                should("parse the values correctly") {
                    nackPacket.mediaSourceSsrc shouldBe sampleMediaSsrc
                    nackPacket.missingSeqNums.shouldContainInOrder(1, 3, 5, 7, 9, 11, 13, 15)
                }
                should("leave the buffer's position after the parsed data") {
                    sampleRtcpFbNackPacketBuf.position() shouldBe sampleRtcpFbNackPacketBuf.limit()
                }
            }
            "from a buffer with multiple NACK blocks" {
                val nackPacket = RtcpFbNackPacket.fromBuffer(multipleNackBlocksBuf)
                should("parse the values correctly") {
                    nackPacket.missingSeqNums shouldContainInOrder listOf(44832, 44838, 44848, 44850, 44854, 44856)

                }
            }
            "from values" {
                val nackPacket =
                    RtcpFbNackPacket.fromValues(sampleHeader, sampleMediaSsrc, sortedSetOf(0, 1, 3, 5, 7, 9, 11, 13, 15))
                should("set the values correctly") {
                    nackPacket.mediaSourceSsrc shouldBe sampleMediaSsrc
                    nackPacket.missingSeqNums.shouldContainInOrder(0, 1, 3, 5, 7, 9, 11, 13, 15)
                }
                "and then serializing it" {
                    "by requesting a buffer" {
                        val buf = nackPacket.getBuffer()
                        should("serialize the data correctly") {
                            buf should haveSameContentAs(sampleRtcpFbNackPacketBuf)
                        }
                    }
                    "to an existing buffer" {
                        val existingBuf = ByteBuffer.allocate(RtcpFbNackPacket.SIZE_BYTES + 8)
                        existingBuf.position(8)
                        nackPacket.serializeTo(existingBuf)
                        should("write the data to the correct place") {
                            existingBuf.subBuffer(8) should haveSameContentAs(sampleRtcpFbNackPacketBuf)
                        }
                        should("leave the buffer's position after the written data") {
                            existingBuf.position() shouldBe existingBuf.limit()
                        }
                    }
                }
            }
            "from values too big to fit into a single NACK block" {
                val rtcpFbNackPacket =
                    RtcpFbNackPacket.fromValues(
                        header = RtcpHeader(senderSsrc = 2725833496),
                        mediaSourceSsrc = 2219854188,
                        missingSeqNums = sortedSetOf(44832, 44838, 44848, 44850, 44854, 44856))
                "and then serializing it" {
                    val buf = rtcpFbNackPacket.getBuffer()
                    should("serialize into multiple nack blocks") {
                        buf should haveSameContentAs(multipleNackBlocksBuf)
                    }
                }
            }
        }
    }
}