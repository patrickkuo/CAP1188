package com.pat.driver.cap1188

import com.pi4j.io.gpio.GpioFactory
import com.pi4j.io.gpio.PinEdge
import com.pi4j.io.gpio.PinPullResistance
import com.pi4j.io.gpio.RaspiPin
import com.pi4j.io.gpio.event.GpioPinListenerDigital
import com.pi4j.io.i2c.I2CBus
import com.pi4j.io.i2c.I2CFactory
import io.reactivex.Observable
import mu.KLogging
import java.nio.ByteBuffer

/**
 * Datasheet :  https://cdn-shop.adafruit.com/datasheets/CAP1188.pdf
 */
class Cap1188<T : Any>(buttonMapping: Map<Int, T>) {
    companion object : KLogging()

    private val device = I2CFactory.getInstance(I2CBus.BUS_1).getDevice(CAP1188_ADDRESS)
    private val reset = GpioFactory.getInstance().provisionDigitalOutputPin(RaspiPin.GPIO_21)
    private val observable: Observable<T> = Observable.create { subscriber ->
        GpioFactory.getInstance().provisionDigitalInputPin(RaspiPin.GPIO_22, PinPullResistance.PULL_DOWN).apply {
            addListener(GpioPinListenerDigital {
                if (it.edge == PinEdge.FALLING) {
                    val main = device.read(CAP1188_MAIN)
                    val newMain =
                        ByteBuffer.allocate(4).putInt(main.and(CAP1188_MAIN_INT.inv())).array().drop(2).toByteArray()
                    device.write(CAP1188_MAIN, newMain)
                    val pressedButton = device.read(CAP1188_SENINPUTSTATUS).toBinaryString().length - 1
                    buttonMapping[pressedButton]?.let(subscriber::onNext)
                }
            })
        }
    }

    init {
        reset()
        logger.info("*****  CAP1188  *****")
        device.read(CAP1188_PRODID).apply {
            require(this == 0x50)
            logger.info("Product ID: 0x${toHexString()}")
        }

        device.read(CAP1188_MANUID).apply {
            require(this == 0x5D)
            logger.info("Manuf. ID: 0x${toHexString()}")
        }

        device.read(CAP1188_REV).apply {
            require(this == 0x83)
            logger.info("Revision: 0x${toHexString()}")
        }

        device.write(CAP1188_LEDLINK, 0xff.toByte())
        device.write(CAP1188_STANDBYCFG, 0x30)
        device.write(0x1f, 0x4f)
        device.write(0x44, 0x41)
        device.write(0x28, 0x00)

        device.write(CAP1188_MAIN, 0)
    }

    fun onPressed(onNext: (T) -> Unit) {
        observable.subscribe(onNext)
    }

    private fun reset() {
        reset.low()
        Thread.sleep(100)
        reset.high()
        Thread.sleep(100)
        reset.low()
    }

    private fun Int.toHexString() = Integer.toHexString(this)
    private fun Int.toBinaryString() = Integer.toBinaryString(this)
}
