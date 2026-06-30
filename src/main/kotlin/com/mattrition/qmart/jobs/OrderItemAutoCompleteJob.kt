package com.mattrition.qmart.jobs

import com.mattrition.qmart.itemlisting.ItemListingService
import com.mattrition.qmart.notification.NotificationService
import com.mattrition.qmart.orderitem.OrderItem
import com.mattrition.qmart.orderitem.OrderItemRepository
import com.mattrition.qmart.orderitem.OrderItemStatus
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

@Service
class OrderItemAutoCompleteJob(
    private val orderItemRepository: OrderItemRepository,
    private val itemListingService: ItemListingService,
    private val notificationService: NotificationService,
) {
    private val log = LoggerFactory.getLogger(OrderItemAutoCompleteJob::class.java)

    @Transactional
    @Scheduled(cron = $$"${jobs.auto-complete.cron}")
    fun autoCompleteShippedOrders() {
        log.info("AutoCompleteJob started")

        val now = OffsetDateTime.now()
        val cutoff = now.minusDays(1)

        val itemsToComplete = orderItemRepository.findAllToComplete(cutoff)

        log.info("Found ${itemsToComplete.size} items to complete.")

        itemsToComplete.forEach { item ->
            item.status = OrderItemStatus.COMPLETED
            item.completedOn = now

            // Increase the amount sold by the quantity
            itemListingService.incrementSold(item.listingId, item.quantity)

            item.order?.buyerId?.let { notifyBuyer(it, item) }
        }

        orderItemRepository.saveAll(itemsToComplete)

        log.info("AutoCompleteJob completed")
    }

    private fun notifyBuyer(
        buyerId: UUID,
        orderItem: OrderItem,
    ) {
        notificationService.createNotification(
            userId = buyerId,
            message = "Item ${orderItem.listingTitle} was delivered!",
            // TODO This should route the user to the individual order containing this item
            route = "/purchases",
        )
    }
}
