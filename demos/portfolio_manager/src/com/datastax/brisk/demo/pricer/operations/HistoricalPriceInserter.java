package com.datastax.brisk.demo.pricer.operations;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

import com.datastax.brisk.demo.pricer.Pricer;
import com.datastax.brisk.demo.pricer.util.Operation;

import org.apache.cassandra.thrift.Column;
import org.apache.cassandra.thrift.ColumnOrSuperColumn;
import org.apache.cassandra.thrift.Mutation;
import org.apache.cassandra.thrift.Cassandra.Client;
import org.apache.cassandra.utils.ByteBufferUtil;
import org.joda.time.LocalDate;

public class HistoricalPriceInserter extends Operation
{
    private static final LocalDate today = LocalDate.fromDateFields(new Date(System.currentTimeMillis()));
       
    public HistoricalPriceInserter(int idx)
    {
        super(idx);
    }

    public void run(Client client) throws IOException
    {

        //Create a stock price per day
        Map<ByteBuffer, Map<String, List<Mutation>>> record = new HashMap<ByteBuffer, Map<String, List<Mutation>>>(tickers.length);

        LocalDate histDate = today.minusDays(index);
        ByteBuffer histDateBuf = ByteBufferUtil.bytes(histDate.toString("YYYYMMDD"));
        
        for(String stock : tickers)
        {
            record.put(ByteBufferUtil.bytes(stock), genDaysPrices(histDateBuf));
        }
        

        long start = System.currentTimeMillis();

        boolean success = false;
        String exceptionMessage = null;

        for (int t = 0; t < session.getRetryTimes(); t++)
        {
            if (success)
                break;

            try
            {
                client.batch_mutate(record, session.getConsistencyLevel());
                success = true;
            }
            catch (Exception e)
            {
                exceptionMessage = getExceptionMessage(e);
                success = false;
            }
        }

        if (!success)
        {
            error(String.format("Operation [%d] retried %d times - error inserting key %s %s%n",
                                index,
                                session.getRetryTimes(),
                                histDate,
                                (exceptionMessage == null) ? "" : "(" + exceptionMessage + ")"));
        }

        session.operations.getAndIncrement();
        session.keys.addAndGet(tickers.length);
        session.latency.getAndAdd(System.currentTimeMillis() - start);
    }

    private Map<String,List<Mutation>> genDaysPrices(ByteBuffer date)
    {
        Map<String, List<Mutation>> prices = new HashMap<String,List<Mutation>>();
             
        Mutation m = new Mutation();
        m.setColumn_or_supercolumn(new ColumnOrSuperColumn().setColumn(
                new Column()
                .setName(date)
                .setValue(ByteBufferUtil.bytes(String.valueOf((double)(Pricer.randomizer.nextDouble()*1000))))
                .setTimestamp(System.currentTimeMillis()) 
                ));
        
        prices.put("StockHist", Arrays.asList(m));
        
        return prices;       
    }
}