package com.meekdev.amnetic.client.light.internal;

import java.util.Arrays;


public final class LightClusterLists {
    private long[] events=new long[0],mask=new long[0],previous=new long[0];
    private int[] entries=new int[0];
    private int length,references;
    public void build(int columns,int rows,int slices,int[] regions,int lights) {
        int clusters=columns*rows*slices,words=(lights+63)/64;
        int eventLength=(columns+1)*rows*slices*words;
        if(events.length<eventLength)events=new long[eventLength];else Arrays.fill(events,0,eventLength,0L);
        if(mask.length!=words){mask=new long[words];previous=new long[words];}
        for(int light=0;light<lights;light++) {
            int at=light*6,min=regions[at],max=regions[at+2];if(min>max)continue;
            long bit=1L<<(light&63);int word=light>>>6;
            for(int z=regions[at+4];z<=regions[at+5];z++)for(int y=regions[at+1];y<=regions[at+3];y++) {
                int row=(y+z*rows)*(columns+1)*words;
                events[row+min*words+word]^=bit;events[row+(max+1)*words+word]^=bit;
            }
        }
        ensure(clusters+1);int next=clusters,last=-1,count=0;references=0;
        for(int row=0;row<rows*slices;row++) {
            Arrays.fill(mask,0L);
            for(int x=0;x<columns;x++) {
                int at=(row*(columns+1)+x)*words;boolean same=last>=0;
                for(int w=0;w<words;w++){mask[w]^=events[at+w];same &= mask[w]==previous[w];}
                if(!same) {
                    count=0;for(long bits:mask)count+=Long.bitCount(bits);
                    ensure(next+count+1);last=next;entries[next++]=count;
                    for(int w=0;w<words;w++) {
                        long bits=mask[w];while(bits!=0){entries[next++]=w*64+Long.numberOfTrailingZeros(bits);bits&=bits-1;}
                        previous[w]=mask[w];
                    }
                }
                entries[row*columns+x]=last;references+=count;
            }
        }
        length=next;
    }
    private void ensure(int capacity){if(entries.length<capacity)entries=Arrays.copyOf(entries,Math.max(capacity,entries.length+entries.length/2));}
    public int[] entries(){return entries;}
    public int length(){return length;}
    public int references(){return references;}
    public void clear(){events=mask=previous=new long[0];entries=new int[0];length=references=0;}
}
