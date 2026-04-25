# Roots Makefile for BKUMENT Microservices

# List of all services that have a local makefile
SERVICES = ai-service \
           api-gateway \
           blog-service \
           communication-service \
           document-service \
           identity-service \
           lms-service \
           profile-service \
           resource-service \
           social-service

.PHONY: all help $(SERVICES)

# Default target: show help
help:
	@echo "BKUMENT Microservices Management"
	@echo "================================"
	@echo "Usage: make [service_name] | make run-all"
	@echo ""
	@echo "Individual services:"
	@for service in $(SERVICES); do \
		echo "  make $$service"; \
	done
	@echo ""
	@echo "All services:"
	@echo "  make run-all      - Start all services in parallel"

# Individual service targets
$(SERVICES):
	@echo ">>> Starting service: $@"
	@$(MAKE) -C $@ run

# Run all services in background
run-all:
	@echo "Starting all services in parallel (background logs)..."
	@for service in $(SERVICES); do \
		(echo ">>> Starting $$service..."; $(MAKE) -C $$service run) & \
	done; \
	wait
