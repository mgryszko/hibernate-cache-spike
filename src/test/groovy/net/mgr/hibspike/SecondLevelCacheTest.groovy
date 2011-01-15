package net.mgr.hibspike

import groovy.sql.Sql
import org.hibernate.SessionFactory
import org.hibernate.cfg.Configuration
import org.hibernate.stat.Statistics
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import static org.junit.Assert.*

class SecondLevelCacheTest {
    private static SessionFactory sessionFactory
    private static sql

    @BeforeClass
    static void setUpSuiteFixture() {
        buildSessionFactory()
        createSqlFacade()
    }

    private static def buildSessionFactory() {
        sessionFactory = new Configuration().configure().buildSessionFactory()
    }

    private static Sql createSqlFacade() {
        sql = Sql.newInstance('jdbc:hsqldb:mem:default', 'sa', '', 'org.hsqldb.jdbcDriver')
    }

    @AfterClass
    static void tearDownSuiteFixture() {
        closeSessionFactory()
    }

    private static def closeSessionFactory() {
        sessionFactory.close()
    }

    private Statistics stats

    @Before
    void setUp() {
        clearStatistics()
        clearCache()
        deleteAllInvoices()
    }

    private def clearStatistics() {
        stats = sessionFactory.statistics
        assertTrue stats.statisticsEnabled
        stats.clear()
    }

    private def clearCache() {
        sessionFactory.cache.evictEntityRegions()
    }

    private def deleteAllInvoices() {
        sql.execute('delete from invoice')
    }

    @Test
    void cacheShould_keepEntity_afterFirstRead() {
        // setup
        def id = insertInvoiceBySql()
        // execute SUT
        getInvoice(id)
        // verify
        assertInvoiceInCache(id)
    }

    @Test
    void cacheStatisticsShould_have1Put0Hits1Miss_afterFirstRead() {
        // setup
        def id = insertInvoiceBySql()
        // execute SUT
        getInvoice(id)
        // verify
        assertSecondLevelCacheStatistics(puts: 1, hits: 0, misses: 1)
    }

    @Test
    void cacheShould_keepEntity_afterThreeReads() {
        // setup
        def id = insertInvoiceBySql()
        // execute SUT
        getInvoice(id)
        getInvoice(id)
        getInvoice(id)
        // verify
        assertInvoiceInCache(id)
    }

    @Test
    void cacheStatisticsShould_have1Put2Hits1Miss_afterThreeReads() {
        // setup
        def id = insertInvoiceBySql()
        // execute SUT
        getInvoice(id)
        getInvoice(id)
        getInvoice(id)
        // verify
        assertSecondLevelCacheStatistics(puts: 1, hits: 2, misses: 1)
    }

    @Test
    void cacheShould_keepEntity_afterFirstWrite() {
        // setup
        def inv = createInvoice()
        // execute SUT
        saveInvoice(inv) // write
        // verify
        assertInvoiceInCache(inv.id)
    }

    @Test
    void cacheStatisticsShould_have1Put0Hits1Miss_afterFirstWrite() {
        // setup
        def inv = createInvoice()
        // execute SUT
        saveInvoice(inv) // write
        // verify
        assertSecondLevelCacheStatistics(puts: 1, hits: 0, misses: 1)
    }

    @Test
    void cacheShould_keepEntity_afterWriteReadWrite() {
        // setup
        def inv = createInvoice()
        // execute SUT
        saveInvoice(inv) // write
        getAndUpdateInvoice(inv.id) // read + write
        // verify
        assertInvoiceInCache(inv.id)
    }

    @Test
    void cacheStatisticsShould_have1Put2Hits1Miss_afterWriteReadWrite() {
        // setup
        def inv = createInvoice()
        // execute SUT
        saveInvoice(inv) // write
        getAndUpdateInvoice(inv.id) // read + write
        // verify
        assertSecondLevelCacheStatistics(puts: 1, hits: 2, misses: 1)
    }

    @Test
    void cacheShould_keepEntity_afterReadWriteRead() {
        // setup
        def id = insertInvoiceBySql()
        // execute SUT
        getAndUpdateInvoice(id) // read + write
        getInvoice(id) // read
        // verify
        assertInvoiceInCache(id)
    }

    @Test
    void cacheStatisticsShould_have1Put2Hits1Miss_afterReadWriteRead() {
        // setup
        def id = insertInvoiceBySql()
        // execute SUT
        getAndUpdateInvoice(id) // read + write
        getInvoice(id) // read
        // verify
        assertSecondLevelCacheStatistics(puts: 1, hits: 2, misses: 1)
    }

    private def createInvoice() {
        return new Invoice(number: 1, customer: 'JetBrains')
    }

    private def insertInvoiceBySql() {
        def inv = createInvoice();
        def id = 1
        sql.execute('insert into invoice (id, number, customer) values (?, ?, ?)', [id, inv.number, inv.customer])
        id
    }

    private def getInvoice(id) {
        def inv = executeInSession { session ->
            session.get(Invoice.class, id)
        }
        assertNotNull inv
    }

    private def saveInvoice(inv) {
        executeInSession { session ->
            session.save inv
        }
    }

    private def getAndUpdateInvoice(id) {
        executeInSession { session ->
            def inv = session.get(Invoice.class, id)
            inv.customer = 'Microsoft'
        }
    }

    private def executeInSession(hibernateOps) {
        def session = sessionFactory.openSession()
        session.beginTransaction()

        def result = hibernateOps(session)

        session.transaction.commit()
        session.close()

        return result
    }

    private def assertSecondLevelCacheStatistics(def expStats) {
        assertEquals('[puts, hits, misses]', [expStats.puts, expStats.hits, expStats.misses],
            [stats.secondLevelCachePutCount as Integer,
                stats.secondLevelCacheHitCount as Integer,
                stats.secondLevelCacheMissCount as Integer])
    }

    private def assertInvoiceInCache(id) {
        assertTrue "invoice id=$id NOT in cache", sessionFactory.cache.containsEntity(Invoice.class, id)
    }
}
