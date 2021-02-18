//OpenTuna icon.icn RLE encoding/decoding tools
//alexparrado (2021)

import java.io.*

//Function that creates icon.icn buffer from raw payload buffer
fun RLEOpenTunaEncode(iShortBuffer: ShortArray, oByteBuffer: MutableList<Byte>) {

    //NOP-Sled size after RLE decoding
    val nopSledSize = 327734
    //Max pay load size for OpenTuna
    val bufferSize = 0x6FF03E
    var maxPayLoadSize = bufferSize - nopSledSize


    val payLoadSize = iShortBuffer.size

    //Reads support binary files
    val header = File("res/opentuna-header.bin").readBytes()
    val nopSled = File("res/opentuna-nop-sled.bin").readBytes()
    val tail = File("res/opentuna-tail.bin").readBytes()



    //Remaining payload
    maxPayLoadSize -= payLoadSize

    //If is there room
    if (maxPayLoadSize >= 0) {

        var auxBuffer = mutableListOf<Byte>()

        //Injects NOP-sled
        nopSled.map { auxBuffer.add(it) }

        //RLE encoding of payload in chunks of 254 halfwords
        RLEEncode(iShortBuffer, auxBuffer, 254)

        //Zero pad in chunks of 32752 halfwords
        var nChunks = (maxPayLoadSize / 0x7ff0);
        var lastChunkSize = maxPayLoadSize - 0x7ff0 * nChunks

        for (i in 1..nChunks) {
            auxBuffer.add(0x7ff0.toUInt().toByte())
            auxBuffer.add(0x7ff0.toUInt().shr(8).and(255u).toByte())
            auxBuffer.add(0)
            auxBuffer.add(0)
        }

        auxBuffer.add(lastChunkSize.toUInt().toByte())
        auxBuffer.add(lastChunkSize.toUInt().shr(8).and(255u).toByte())
        auxBuffer.add(0)
        auxBuffer.add(0)

        //Injects tail to aux buffer. Return address repetitions may require manual adjustment (trial and error)
        tail.map { auxBuffer.add(it) }

        //Size of RLE encoded data
        val rleSize = auxBuffer.size

        //Injects header
        header.map { oByteBuffer.add(it) }

        //Injects size of RLE encoded data
        oByteBuffer.add(rleSize.toUInt().toByte())
        oByteBuffer.add(rleSize.toUInt().shr(8).and(255u).toByte())
        oByteBuffer.add(rleSize.toUInt().shr(16).and(255u).toByte())
        oByteBuffer.add(rleSize.toUInt().shr(24).and(255u).toByte())

       // println(rleSize)


        //Injects aux buffer
        auxBuffer.map { oByteBuffer.add(it) }


    }
}

//This function performs RLE encoding of input buffer
fun RLEEncode(iShortBuffer: ShortArray, oByteBuffer: MutableList<Byte>, chunkSize: Int) {

    //Number of chunks
    var nChunks = (iShortBuffer.size / chunkSize);
    var lastChunkSize = iShortBuffer.size - chunkSize * nChunks
    var auxSize: Short

    var processedWords = 0

    //Two's complement of chunk size
    auxSize = (65536 - chunkSize).toShort()
    for (i in 1..nChunks) {

        oByteBuffer.add(auxSize.toUInt().toByte())
        oByteBuffer.add(auxSize.toUInt().shr(8).and(255u).toByte())

        for (k in 1..chunkSize) {

            oByteBuffer.add(iShortBuffer[processedWords].toUInt().toByte())
            oByteBuffer.add(iShortBuffer[processedWords].toUInt().shr(8).and(255u).toByte())
            processedWords++
        }
    }
    //Last chunk
    auxSize = (65536 - lastChunkSize).toShort()
    oByteBuffer.add(auxSize.toUInt().toByte())
    oByteBuffer.add(auxSize.toUInt().shr(8).and(255u).toByte())

    for (k in 1..lastChunkSize) {

        oByteBuffer.add(iShortBuffer[processedWords].toUInt().toByte())
        oByteBuffer.add(iShortBuffer[processedWords].toUInt().shr(8).and(255u).toByte())
        processedWords++
    }


}

//This functions extracts payload from icon and performs RLE decoding
fun RLEDecode(iShortBuffer: ShortArray, oByteBuffer: MutableList<Byte>) {

    var nBytes = 0
    var processedWords = 0
    var j = 0
    var flag = false
    while (processedWords < iShortBuffer.size) {
        var nCopy = iShortBuffer[processedWords].toInt()


        if (nCopy > 0) {
            flag = false
        } else {
            flag = true
            nCopy = -nCopy


        }
       // println(nCopy)
        nBytes += nCopy * 2
        if (flag) {
            for (i in 1..(nCopy)) {

                oByteBuffer.add(iShortBuffer[processedWords + 1].toUInt().toByte())
                oByteBuffer.add(iShortBuffer[processedWords + 1].toUInt().shr(8).and(255u).toByte())
                processedWords++
                j++

            }
            processedWords += 1


        } else {
            processedWords += 2

        }


    }

  //  println(nBytes)
}

//Main function
fun main(args: Array<String>) {

    if (args.size < 3) {
        println("Usage to encode: -e <payload-binary> <icon-file>")
        println("Usage to decode: -d <icon-file> <payload-binary>")
    } else {

        //Decode flag
        if (args[0].equals("-d")) {
            val f = File(args[1])

            //offset of RLE encoded data
            val index = 0x15c

            val buffer = f.inputStream().readBytes()

            val size = buffer.size - index


            val ishortBuffer = ShortArray(size / 2) {
                (buffer[it * 2 + index].toUByte().toInt() + (buffer[it * 2 + index + 1].toInt() shl 8)).toShort()

            }

            // println(size)

            var oByteBuffer = mutableListOf<Byte>()
            RLEDecode(ishortBuffer, oByteBuffer)

            val outpuf = File(args[2])


            val outputStream = outpuf.outputStream()

            outputStream.write(oByteBuffer.toByteArray())
        }
        //Encode flag
        else if (args[0].equals("-e")) {
            val f = File(args[1])

            val buffer = f.inputStream().readBytes()

            val ishortBuffer = ShortArray(buffer.size / 2) {
                (buffer[it * 2].toUByte().toInt() + (buffer[it * 2 + 1].toInt() shl 8)).toShort()
            }
            var oByteBuffer = mutableListOf<Byte>()
            RLEOpenTunaEncode(ishortBuffer, oByteBuffer)

            val outpuf = File(args[2])

            val outputStream = outpuf.outputStream()
            outputStream.write(oByteBuffer.toByteArray())


        } else {
            println("Usage to encode: -e <payload-binary> <icon-file>")
            println("Usage to decode: -d <icon-file> <payload-binary>")
        }

    }
}