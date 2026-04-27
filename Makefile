# Root Makefile for BKUMENT Microservices

# List of all sub-services
SERVICES = ai-service \
           api-gateway \
           blog-service \
           communication-service \
           document-service \
           email-service \
           identity-service \
           lms-service \
           notification-service \
           profile-service \
           resource-service \
           social-service

PWD = $(shell pwd)

.PHONY: all help run-all $(SERVICES)

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
	@echo "Automation (macOS only):"
	@echo "  make run-all      - Opens a new terminal for EACH service and runs it"

# Individual service targets (runs in current terminal)
$(SERVICES):
	@echo ">>> Starting service: $@"
	@$(MAKE) -C $@ run

# Run each service in a NEW terminal window (macOS)
run-all:
	@echo "Launching $(words $(SERVICES)) services in separate terminals..."
	@for service in $(SERVICES); do \
		echo "Opening terminal for $$service..."; \
		osascript -e "tell application \"Terminal\" to do script \"cd $(PWD)/$$service && make run\""; \
	done
	@echo "Done. All services are starting in separate windows."
