package com.codex.blacklight.gametest;

import com.mojang.datafixers.util.Either;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.neoforged.neoforge.gametest.*;

/** A real region worker with deterministic mailbox ordering, never the user's world. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class NativeChunkIoGameTest {
    private static final class Worker extends IOWorker {
        Worker(Path directory){super(new net.minecraft.world.level.chunk.storage.RegionStorageInfo("Blacklight QA",net.minecraft.world.level.Level.OVERWORLD,"chunk"),directory,false);}
    }
    @SuppressWarnings("unchecked")
    @GameTest(template="wall_test",timeoutTicks=100,batch="native_io_pending_snapshot")
    public static void pendingReadMustNotBorrowMutableNbtFromTheAsyncWriter(GameTestHelper h) throws Exception {
        var directory=Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")),"blacklight-io-alias-");
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var worker=new Worker(directory)){
            var submit=IOWorker.class.getDeclaredMethod("submitTask",Supplier.class);submit.setAccessible(true);
            Supplier<Either<Void,Exception>> barrier=()->{
                entered.countDown();
                try{return release.await(5,TimeUnit.SECONDS)?Either.left(null):Either.right(new java.io.IOException("QA mailbox barrier timed out"));}
                catch(InterruptedException interrupted){Thread.currentThread().interrupt();return Either.right(interrupted);}
            };
            var blocker=(CompletableFuture<Void>)submit.invoke(worker,barrier);
            h.assertTrue(entered.await(4,TimeUnit.SECONDS),"actual worker executes the independent IO barrier");
            var position=new ChunkPos(38,44);var original=new CompoundTag();var nested=new CompoundTag();
            original.putInt("DataVersion",3465);nested.putString("UnknownOrgan","othermod:future_organ");nested.putLong("Count",Long.MAX_VALUE);
            original.put("ForeignAndBlacklight",nested);var expected=original.copy();
            var stored=worker.store(position,original);var read=worker.loadAsync(position);release.countDown();blocker.get(5,TimeUnit.SECONDS);
            var reader=read.get(5,TimeUnit.SECONDS).orElseThrow();
            h.assertTrue(reader!=original&&reader.getCompound("ForeignAndBlacklight")!=nested,
                    "pending read must return an independent deep snapshot, not the original mutable write tree; sameRoot="+(reader==original)+" directory="+directory);
            reader.putInt("ReaderUpgrade",7);reader.getCompound("ForeignAndBlacklight").putString("UnknownOrgan","reader:changed");
            h.assertTrue(original.equals(expected),"a reader's data upgrade cannot mutate the producer or queued write");
            stored.get(5,TimeUnit.SECONDS);worker.synchronize(true).get(5,TimeUnit.SECONDS);
            var disk=worker.loadAsync(position).get(5,TimeUnit.SECONDS).orElseThrow();
            h.assertTrue(disk.equals(expected),"real region disk retains the exact accepted NBT, including unknown extension fields");
            worker.store(position,null).get(5,TimeUnit.SECONDS);worker.synchronize(true).get(5,TimeUnit.SECONDS);
            h.assertTrue(worker.loadAsync(position).get(5,TimeUnit.SECONDS).isEmpty(),"real queued deletion still completes and loads empty");h.succeed();
        }finally{release.countDown();}
    }
}
