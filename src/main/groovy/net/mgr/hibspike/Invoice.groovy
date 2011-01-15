package net.mgr.hibspike

import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import org.hibernate.annotations.Cache
import org.hibernate.annotations.CacheConcurrencyStrategy

@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
class Invoice {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    Integer id
    Integer number
    String customer

    boolean equals(o) {
        if (this.is(o)) return true

        if (!(o instanceof Invoice)) return false

        Invoice invoice = o

        if (customer != invoice.customer) return false
        if (number != invoice.number) return false

        true
    }

    int hashCode() {
        def result = number != null ? number.hashCode() : 0
        31 * result + (customer != null ? customer.hashCode() : 0)
    }

    String toString() {
        "Invoice{id=$id, number=$number, customer=$customer}"
    }
}