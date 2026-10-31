package com.codex.blacklight.util;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;

/** Lossless JSON token transform. Restores the original UTF-8 bytes, not a reserialized JSON tree. */
public final class CompactJson {
    private static final int MAGIC=0x424a4332; // BJC2
    private CompactJson(){}
    public static byte[] encode(byte[] source)throws IOException{
        var skeleton=new ByteArrayOutputStream(source.length);
        var tags=new ByteArrayOutputStream();var words=new ByteArrayOutputStream();var raw=new ByteArrayOutputStream();
        var values=new DataOutputStream(words);var literals=new DataOutputStream(raw);long[] history=new long[16];int number=0;
        boolean quoted=false,escaped=false;
        for(int at=0;at<source.length;){
            int b=source[at]&255;
            if(b==0)throw new IOException("NUL is not valid raw JSON");
            if(quoted){
                skeleton.write(b);at++;
                if(escaped)escaped=false;else if(b=='\\')escaped=true;else if(b=='\"')quoted=false;
                continue;
            }
            if(b=='\"'){quoted=true;skeleton.write(b);at++;continue;}
            if(b=='-'||b>='0'&&b<='9'){
                int begin=at++;
                while(at<source.length&&numeric(source[at]&255))at++;
                String token=new String(source,begin,at-begin,StandardCharsets.US_ASCII);
                try{
                    double value=Double.parseDouble(token);int style=-1;
                    if(Double.isFinite(value))for(int candidate=0;candidate<3;candidate++)if(token.equals(format(value,candidate))){style=candidate;break;}
                    if(style>=0){
                        tags.write(style+1);long bits=Double.doubleToRawLongBits(value);int atHistory=number++&15;
                        values.writeLong(bits^history[atHistory]);history[atHistory]=bits;
                    }else{tags.write(0);byte[] bytes=token.getBytes(StandardCharsets.US_ASCII);literals.writeInt(bytes.length);literals.write(bytes);}
                }catch(NumberFormatException e){tags.write(0);byte[] bytes=token.getBytes(StandardCharsets.US_ASCII);literals.writeInt(bytes.length);literals.write(bytes);}
                skeleton.write(0);
            }else{skeleton.write(b);at++;}
        }
        if(quoted)throw new IOException("Unterminated JSON string");
        var result=new ByteArrayOutputStream();var out=new DataOutputStream(result);
        out.writeInt(MAGIC);out.writeInt(source.length);out.writeInt(skeleton.size());out.writeInt(tags.size());out.writeInt(number);out.writeInt(raw.size());
        out.write(sha(source));skeleton.writeTo(out);tags.writeTo(out);
        byte[] binary=words.toByteArray();
        for(int lane=0;lane<8;lane++)for(int i=lane;i<binary.length;i+=8)out.writeByte(binary[i]);
        raw.writeTo(out);out.flush();
        return result.toByteArray();
    }
    private static boolean numeric(int b){return b>='0'&&b<='9'||b=='-'||b=='+'||b=='.'||b=='e'||b=='E';}
    private static String format(double value,int style){
        if(style==2)return value>=Long.MIN_VALUE&&value<Long.MAX_VALUE&&value==Math.rint(value)?Long.toString((long)value):"";
        String text=Double.toString(value);
        if(style==0)return text;
        int e=text.indexOf('E');if(e<0)return text;
        double absolute=Math.abs(value);
        if(absolute>=1e-4&&absolute<1e16){
            String plain=BigDecimal.valueOf(value).toPlainString();return plain.indexOf('.')<0?plain+".0":plain;
        }
        String mantissa=text.substring(0,e);if(mantissa.endsWith(".0"))mantissa=mantissa.substring(0,mantissa.length()-2);
        int exponent=Integer.parseInt(text.substring(e+1)),size=Math.abs(exponent);
        return mantissa+"e"+(exponent<0?"-":"+")+(size<10?"0":"")+size;
    }
    public static byte[] decode(byte[] packed,int maximumBytes)throws IOException{
        if(maximumBytes<0)throw new IOException("Invalid JSON size limit");
        try(var in=new DataInputStream(new ByteArrayInputStream(packed))){
            if(in.readInt()!=MAGIC)throw new IOException("Unsupported compact JSON");
            int length=in.readInt(),skeletonLength=in.readInt(),tagCount=in.readInt(),valueCount=in.readInt(),rawLength=in.readInt();
            if(length<0||length>maximumBytes||skeletonLength<0||skeletonLength>length||tagCount<0||tagCount>skeletonLength
                    ||valueCount<0||valueCount>tagCount||rawLength<0
                    ||56L+skeletonLength+tagCount+8L*valueCount+rawLength!=packed.length)throw new IOException("Invalid compact JSON bounds");
            byte[] expected=in.readNBytes(32),skeleton=in.readNBytes(skeletonLength),tags=in.readNBytes(tagCount);
            int plane=56+skeletonLength+tagCount,rawOffset=plane+8*valueCount,tagAt=0,valueAt=0;
            var literal=new DataInputStream(new ByteArrayInputStream(packed,rawOffset,rawLength));long[] history=new long[16];
            var out=new ByteArrayOutputStream(length);
            for(byte next:skeleton){
                if(next!=0){if(out.size()==length)throw new IOException("Compact JSON overflow");out.write(next);continue;}
                if(tagAt==tags.length)throw new IOException("Missing compact JSON tag");
                int tag=tags[tagAt++]&255;byte[] value;
                if(tag==0){
                    int size=literal.readInt();if(size<1||size>length-out.size()||size>literal.available())throw new IOException("Invalid raw JSON number");
                    value=literal.readNBytes(size);
                }else{
                    if(tag>3||valueAt==valueCount)throw new IOException("Invalid compact JSON number tag");
                    long delta=0;for(int lane=0;lane<8;lane++)delta=(delta<<8)|(packed[plane+lane*valueCount+valueAt]&255L);
                    int old=valueAt++&15;long bits=delta^history[old];history[old]=bits;double number=Double.longBitsToDouble(bits);
                    if(!Double.isFinite(number))throw new IOException("Non-finite compact JSON number");
                    value=format(number,tag-1).getBytes(StandardCharsets.US_ASCII);
                }
                if(value.length>length-out.size())throw new IOException("Compact JSON overflow");out.write(value);
            }
            if(tagAt!=tagCount||valueAt!=valueCount||literal.available()!=0||out.size()!=length)throw new IOException("Truncated compact JSON");
            byte[] result=out.toByteArray();if(!MessageDigest.isEqual(expected,sha(result)))throw new IOException("Compact JSON identity mismatch");
            return result;
        }
    }
    private static byte[] sha(byte[] input){try{return MessageDigest.getInstance("SHA-256").digest(input);}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
}
