package com.moakiee.ae2lt.packaged.resource;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MaidPowerAmountsTest {
    @Test void decimalCostsAndSubcentRejection() {
        assertEquals(60, MaidPowerAmounts.recipeCost(0.6F));
        assertEquals(1, MaidPowerAmounts.recipeCost(0.01F));
        assertEquals(500, MaidPowerAmounts.recipeCost(5F));
        assertEquals(-1, MaidPowerAmounts.recipeCost(0.001F));
        assertEquals(-1, MaidPowerAmounts.recipeCost(Float.NaN));
        assertEquals(-1, MaidPowerAmounts.recipeCost(5.01F));
    }
    @Test void repeatedSmallTransfersNeverCreatePower() {
        float power=0;
        long balance=0;
        for(int i=0;i<10000;i++) {
            var deposited=MaidPowerAmounts.transfer(power,10000,1,true);
            balance+=deposited.moved(); power=deposited.after();
            assertEquals(balance,MaidPowerAmounts.stored(power));
        }
        while(balance>0) {
            var withdrawn=MaidPowerAmounts.transfer(power,10000,Math.min(7,balance),false);
            assertTrue(withdrawn.moved()>0);balance-=withdrawn.moved();power=withdrawn.after();
            assertEquals(balance,MaidPowerAmounts.stored(power));
        }
    }
    @Test void hugeFloatsDoNotOverpaySmallReceipts() {
        for(float initial:new float[]{100000F,1000000F,10000000F,1e20F}) {
            for(boolean insert:new boolean[]{true,false}) {
                var t=MaidPowerAmounts.transfer(initial,initial*2,1,insert);
                assertTrue(t.moved()>=0 && t.moved()<=1);
                if(t.moved()==0)assertEquals(initial,t.after());
            }
        }
    }
}
