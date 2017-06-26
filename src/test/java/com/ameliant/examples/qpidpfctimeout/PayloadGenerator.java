package com.ameliant.examples.qpidpfctimeout;

/**
 * Generates binary payloads of a specified size.
 * Created by jkorab on 26/06/17.
 */
class PayloadGenerator {

    byte[] generatePayload(int size) {
        assert (size > 0);
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = 0;
        }
        return bytes;
    }
}
